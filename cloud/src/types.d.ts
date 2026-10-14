/** Plain-text files bundled as strings — wrangler's default rule for `*.txt`. */
declare module '*.txt' {
  const text: string;
  export default text;
}
