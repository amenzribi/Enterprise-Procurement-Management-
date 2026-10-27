// The e2e suite only reads environment variables; this avoids a dependency on @types/node.
declare const process: { env: Record<string, string | undefined> };
