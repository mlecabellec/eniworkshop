declare module 'plantuml-encoder' {
  const encoder: {
    encode(text: string): string;
    decode(encoded: string): string;
  };
  export default encoder;
}
