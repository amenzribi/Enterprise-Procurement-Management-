/**
 * Accepts only internal application paths as a post-sign-in destination, so a crafted
 * `?returnUrl=https://evil.example` or `//evil.example` can never redirect off-site (open redirect).
 */
export function safeReturnUrl(candidate: string | null | undefined, fallback = '/requests'): string {
  if (!candidate || typeof candidate !== 'string') {
    return fallback;
  }
  const value = candidate.trim();
  const isInternalPath =
    value.startsWith('/') &&
    !value.startsWith('//') &&
    !value.startsWith('/\\') &&
    !value.includes('\\') &&
    !hasControlCharacter(value) &&
    !/^\/+[a-z][a-z0-9+.-]*:/i.test(value);
  if (!isInternalPath || value.startsWith('/sign-in')) {
    return fallback;
  }
  return value;
}

function hasControlCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index++) {
    if (value.charCodeAt(index) < 0x20) {
      return true;
    }
  }
  return false;
}
