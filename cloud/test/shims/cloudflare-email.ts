/** Stand-in for `cloudflare:email`. The tests set no MAIL binding, so nothing is ever sent. */
export class EmailMessage {
  constructor(readonly from: string, readonly to: string, readonly raw: unknown) {}
}
