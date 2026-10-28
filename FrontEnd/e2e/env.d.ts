// The e2e suite only reads environment variables; this avoids a dependency on @types/node.
declare const process: { env: Record<string, string | undefined> };

// The few Node APIs the suite uses to read the e-mails the backend writes in development (S2-AC-14).
declare const __dirname: string;

declare module 'node:fs' {
  export function readFileSync(path: string, encoding: 'utf8'): string;
  export function readdirSync(path: string): string[];
  export function statSync(path: string): { mtimeMs: number };
}

declare module 'node:path' {
  export function join(...parts: string[]): string;
  export function resolve(...parts: string[]): string;
}
