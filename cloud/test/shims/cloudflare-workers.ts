/** Stand-in for `cloudflare:workers` under Node: the base class is all the code here needs. */
export class DurableObject<E = unknown> {
  ctx: unknown;
  env: E;
  constructor(ctx: unknown, env: E) {
    this.ctx = ctx;
    this.env = env;
  }
}
