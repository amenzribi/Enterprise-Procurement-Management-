import en from '../../../../public/i18n/en.json';
import fr from '../../../../public/i18n/fr.json';

interface Tree {
  [key: string]: string | Tree;
}

function flatten(tree: Tree, prefix = ''): Map<string, string> {
  const entries = new Map<string, string>();
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') {
      entries.set(path, value);
    } else {
      flatten(value, path).forEach((nested, nestedPath) => entries.set(nestedPath, nested));
    }
  }
  return entries;
}

const placeholders = (text: string) => [...text.matchAll(/\{\{\s*(\w+)\s*\}\}/g)].map((match) => match[1]).sort();

describe('translation files', () => {
  const english = flatten(en as Tree);
  const french = flatten(fr as Tree);

  it('define exactly the same keys in English and French', () => {
    expect([...french.keys()].sort()).toEqual([...english.keys()].sort());
  });

  it('use the same placeholders in both languages', () => {
    for (const [key, text] of english) {
      expect(placeholders(french.get(key) ?? ''), key).toEqual(placeholders(text));
    }
  });

  it('have no empty texts', () => {
    const empty = [...english, ...french].filter(([, text]) => text.trim() === '').map(([key]) => key);
    expect(empty).toEqual([]);
  });
});
