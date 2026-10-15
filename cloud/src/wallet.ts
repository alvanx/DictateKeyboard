import { DurableObject } from 'cloudflare:workers';
import { LEGACY_REWORD_SECONDS, TYPICAL_REWORD_SECONDS, limitsFrom, type Env } from './config';
import {
  allowanceLeft,
  bookDaily,
  correctDaily,
  nextUtcMidnight,
  usedToday,
  utcDay,
  type DailyBucket,
} from './entitlement';

/**
 * One credit account.
 *
 * **Why a Durable Object and not a database row:** Cloudflare guarantees exactly one instance
 * per wallet worldwide and runs its calls one after another. That makes "check whether there
 * is enough and deduct it" a single indivisible step. With an ordinary database you would
 * have to lock — otherwise two simultaneous dictations by the same user read the same
 * balance and both deduct from it.
 *
 * Everything is counted in **seconds**, not minutes: a 37-second dictation should cost 37
 * seconds rather than being rounded up to a minute.
 *
 * And in seconds *only*. There used to be a second balance counting rewordings, which meant the
 * account could not say what it was worth — a rewording deducted one unit whether it cost a fifth
 * of a second or sixteen. One balance, and every service priced into it, makes the remaining
 * seconds an exact statement about money: see `SECOND_VALUE_NANO` in config.ts.
 *
 * **Plus (billing spec, sections 5 and 8).** With the subscription the account also carries
 * `entitledUntil` — the one number a paid request is checked against, written here by every event
 * that changes it (verify, notification, reconcile, grant) — and a daily bucket that resets at
 * 00:00 UTC. A request is booked against the bucket while the account is entitled. Seconds bought
 * as credit packs before the subscription existed stay spendable once Plus is not active: they were
 * paid for, and the migration that introduced the subscription took nothing away.
 */

/** What is written to storage. */
interface StoredState {
  secondsLeft: number;
  secondsBought: number;
  secondsUsed: number;
  status: 'active' | 'blocked';
  /** Only ever present on accounts last written before rewordings were priced. See [read]. */
  rewordsLeft?: number;
  /** Unix ms. Absent or past: no Plus. */
  entitledUntil?: number;
  daily?: DailyBucket;
}

export interface WalletState {
  secondsLeft: number;
  secondsBought: number;
  secondsUsed: number;
  status: 'active' | 'blocked';
  /**
   * Not a balance — an estimate, derived on the way out: what the remaining seconds are worth in
   * rewordings of ordinary length. The app has always shown this number and it is more honest
   * now than when it was a counter, because it follows what a rewording actually costs.
   */
  rewordsLeft: number;
  /** Unix ms; 0 when the account was never entitled. */
  entitledUntil: number;
  plan: 'plus' | 'free';
  /** Seconds booked against today's allowance. */
  dailyUsedSeconds: number;
  dailyAllowanceSeconds: number;
  /** 00:00 UTC after now. */
  dailyResetsAt: number;
}

/**
 * Which counter a request was booked on — the daily allowance or the credit balance — and on which
 * day. A refund or correction has to go back where the charge came from.
 */
export interface Booking {
  source: 'daily' | 'balance';
  day: string;
}

export type DebitReason = 'blocked' | 'rate_limited' | 'not_entitled' | 'daily_limit';

export type DebitResult =
  | { ok: true; state: WalletState; booking: Booking }
  | {
      ok: false;
      reason: DebitReason;
      state: WalletState;
      /** A rate-limited refusal that is not the first in its minute, so not worth a row of its own. */
      repeat?: boolean;
    };

/** What older callers meant before there were two counters: the credit balance. */
const BALANCE: Booking = { source: 'balance', day: '' };

const EMPTY: StoredState = {
  secondsLeft: 0,
  secondsBought: 0,
  secondsUsed: 0,
  status: 'active',
};

export class Wallet extends DurableObject<Env> {
  /**
   * Timestamps of recent requests, in memory only.
   *
   * Deliberately not persisted: the rate limit exists to slow bursts down, and if the object
   * was evicted for lack of traffic the burst is over anyway. One write per request would be
   * too expensive for that.
   */
  private hits: number[] = [];

  /**
   * When the last rate-limited refusal that was reported in full went out, in memory like [hits].
   * A flood is refused on every request, but only its first refusal a minute is worth a row in the
   * usage log; one per request was the write volume the limit exists to prevent.
   */
  private limitedAt: number | null = null;

  /**
   * Reads the balance, converting an account that still carries the old separate rewording
   * allowance on the way past.
   *
   * Done here rather than as a database migration because the balance lives in this object and
   * nowhere else — there is no table to sweep. It runs once per account, on its next touch.
   *
   * The rate is `LEGACY_REWORD_SECONDS` and **not** what a rewording costs today. Those were the
   * same number when this was written; they parted company when the typical rewording was measured
   * rather than assumed, and had they stayed tied, that measurement would have quietly halved an
   * old allowance on the day it landed. A past conversion keeps its own rate.
   */
  private async read(): Promise<StoredState> {
    const stored = (await this.ctx.storage.get<StoredState>('state')) ?? EMPTY;
    if (stored.rewordsLeft === undefined) return stored;

    const converted: StoredState = {
      secondsLeft: stored.secondsLeft + Math.max(0, stored.rewordsLeft) * LEGACY_REWORD_SECONDS,
      secondsBought: stored.secondsBought,
      secondsUsed: stored.secondsUsed,
      status: stored.status,
    };
    await this.write(converted);
    return converted;
  }

  private async write(state: StoredState): Promise<void> {
    await this.ctx.storage.put('state', state);
  }

  /** The stored balance plus the derived figures — what every caller sees. */
  private view(state: StoredState, now = Date.now()): WalletState {
    const entitledUntil = state.entitledUntil ?? 0;
    return {
      secondsLeft: state.secondsLeft,
      secondsBought: state.secondsBought,
      secondsUsed: state.secondsUsed,
      status: state.status,
      rewordsLeft: Math.max(0, Math.floor(state.secondsLeft / TYPICAL_REWORD_SECONDS)),
      entitledUntil,
      plan: entitledUntil > now ? 'plus' : 'free',
      dailyUsedSeconds: usedToday(state.daily, now),
      dailyAllowanceSeconds: this.allowance(),
      dailyResetsAt: nextUtcMidnight(now),
    };
  }

  /** Read per call, so changing `DAILY_ALLOWANCE_SECONDS` needs no migration of any account. */
  private allowance(): number {
    return limitsFrom(this.env).dailyAllowanceSeconds;
  }

  async state(): Promise<WalletState> {
    return this.view(await this.read());
  }

  /**
   * Books a request, if the account may make one. The only way anything is ever spent.
   *
   * Happens **before** the model is called, not after: bill last and every dropped connection
   * is a giveaway. If the call fails, [refund] puts it back.
   *
   * The order of the checks: too fast first, then blocked, then Plus — today's allowance while it
   * is active, the old credit balance when it is not — and only then "subscribe".
   *
   * Every attempt counts toward the rate limit, a refused one too. Counted only on success, an
   * account that was blocked or had no Plus was never limited at all: exactly the states in which
   * someone keeps hammering, each attempt still costing an authentication, a read of the upload, a
   * row in the usage log and, for "not entitled", a recheck with Google.
   *
   * [alreadyCounted] is for the second try in `debitPaid` after a recheck: the same request,
   * so it is neither limited nor counted a second time.
   */
  async debit(seconds: number, rateLimitPerMinute: number, alreadyCounted = false): Promise<DebitResult> {
    const state = await this.read();
    const now = Date.now();

    if (!alreadyCounted) {
      this.hits = this.hits.filter((t) => now - t < 60_000);
      if (this.hits.length >= rateLimitPerMinute) {
        const repeat = this.limitedAt !== null && now - this.limitedAt < 60_000;
        if (!repeat) this.limitedAt = now;
        return { ok: false, reason: 'rate_limited', state: this.view(state, now), repeat };
      }
      this.hits.push(now);
    }

    if (state.status === 'blocked') return { ok: false, reason: 'blocked', state: this.view(state, now) };

    const wantSeconds = Math.ceil(seconds);

    if ((state.entitledUntil ?? 0) > now) {
      if (!allowanceLeft(state.daily, this.allowance(), now)) {
        return { ok: false, reason: 'daily_limit', state: this.view(state, now) };
      }
      const next: StoredState = { ...state, daily: bookDaily(state.daily, wantSeconds, now) };
      await this.write(next);
      return { ok: true, state: this.view(next, now), booking: { source: 'daily', day: utcDay(now) } };
    }

    if (state.secondsLeft >= wantSeconds && wantSeconds > 0) {
      const next: StoredState = {
        ...state,
        secondsLeft: state.secondsLeft - wantSeconds,
        secondsUsed: state.secondsUsed + wantSeconds,
      };
      await this.write(next);
      return { ok: true, state: this.view(next, now), booking: BALANCE };
    }

    return { ok: false, reason: 'not_entitled', state: this.view(state, now) };
  }

  /** Puts a booking back — for when the model never answered at all. */
  async refund(seconds: number, booking: Booking = BALANCE): Promise<WalletState> {
    const state = await this.read();
    const back = Math.ceil(seconds);
    if (booking.source === 'daily') {
      const next: StoredState = { ...state, daily: correctDaily(state.daily, booking.day, -back) };
      await this.write(next);
      return this.view(next);
    }
    const next: StoredState = {
      ...state,
      secondsLeft: state.secondsLeft + back,
      secondsUsed: Math.max(0, state.secondsUsed - back),
    };
    await this.write(next);
    return this.view(next);
  }

  /**
   * Corrects a deduction up or down once the real duration is known.
   *
   * Only needed for non-WAV files, which are estimated generously up front. A negative
   * [deltaSeconds] gives back what was over-charged.
   */
  async adjust(deltaSeconds: number, booking: Booking = BALANCE): Promise<WalletState> {
    const state = await this.read();
    const delta = Math.round(deltaSeconds);
    if (booking.source === 'daily') {
      const next: StoredState = { ...state, daily: correctDaily(state.daily, booking.day, delta) };
      await this.write(next);
      return this.view(next);
    }
    const next: StoredState = {
      ...state,
      // Floored at zero: a correction must never push an account into the red.
      secondsLeft: Math.max(0, state.secondsLeft - delta),
      secondsUsed: Math.max(0, state.secondsUsed + delta),
    };
    await this.write(next);
    return this.view(next);
  }

  /** A purchase, a gift, or a correction from the dashboard. */
  async credit(seconds: number): Promise<WalletState> {
    const state = await this.read();
    const next: StoredState = {
      ...state,
      secondsLeft: state.secondsLeft + seconds,
      secondsBought: state.secondsBought + Math.max(0, seconds),
    };
    await this.write(next);
    return this.view(next);
  }

  /**
   * Takes credit away, into the negative if need be.
   *
   * For a refunded payment: someone who buys, spends and then claws their money back should
   * not be left sitting at zero, free to buy again. The shortfall stays until it is settled.
   */
  async claw(seconds: number): Promise<WalletState> {
    const state = await this.read();
    const next: StoredState = { ...state, secondsLeft: state.secondsLeft - seconds };
    await this.write(next);
    return this.view(next);
  }

  /**
   * Writes the new "entitled until" (unix ms; 0 for none).
   *
   * The ledger in D1 decides the value — see `refreshWalletEntitlement` — and this is the copy the
   * hot path reads, so a paid request costs one call instead of a query over subscriptions.
   */
  async setEntitlement(entitledUntil: number): Promise<WalletState> {
    const state = await this.read();
    const next: StoredState = { ...state, entitledUntil: Math.max(0, Math.round(entitledUntil)) };
    await this.write(next);
    return this.view(next);
  }

  async setStatus(status: 'active' | 'blocked'): Promise<WalletState> {
    const state = await this.read();
    const next: StoredState = { ...state, status };
    await this.write(next);
    return this.view(next);
  }

  /** Absorbs another account's credit — the commonest support case after a device change. */
  async absorb(other: WalletState): Promise<WalletState> {
    const state = await this.read();
    const next: StoredState = {
      ...state,
      secondsLeft: state.secondsLeft + other.secondsLeft,
      secondsBought: state.secondsBought + other.secondsBought,
      secondsUsed: state.secondsUsed + other.secondsUsed,
    };
    await this.write(next);
    return this.view(next);
  }

  /** Empties an account completely — meant for the source side of a merge. */
  async drain(): Promise<WalletState> {
    const state = await this.read();
    const next: StoredState = { ...state, secondsLeft: 0, status: 'blocked' };
    await this.write(next);
    return this.view(next);
  }

  /**
   * Wipes the object's storage — for an account the user asked to have deleted.
   *
   * Different from [drain], and the difference is the whole point: draining leaves a zeroed record
   * behind, this leaves nothing. Deleting the row in D1 alone would not be enough, because the
   * authoritative balance lives here; it would come back as a working account the moment anything
   * addressed this id again.
   *
   * Returns what was there, so the caller can tell the user exactly how much credit they are about
   * to lose before they confirm — and so the deletion can be logged with a number in it.
   */
  async erase(): Promise<WalletState> {
    const state = this.view(await this.read());
    await this.ctx.storage.deleteAll();
    return state;
  }
}
