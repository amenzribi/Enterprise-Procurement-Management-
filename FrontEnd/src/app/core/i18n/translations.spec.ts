import en from '../../../../public/i18n/en.json';
import fr from '../../../../public/i18n/fr.json';
import ar from '../../../../public/i18n/ar.json';

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
  const others = { fr: flatten(fr as Tree), ar: flatten(ar as Tree) };

  for (const [language, texts] of Object.entries(others)) {
    it(`define exactly the same keys in English and ${language}`, () => {
      expect([...texts.keys()].sort()).toEqual([...english.keys()].sort());
    });

    it(`use the same placeholders in English and ${language}`, () => {
      for (const [key, text] of english) {
        expect(placeholders(texts.get(key) ?? ''), key).toEqual(placeholders(text));
      }
    });
  }

  it('have no empty texts', () => {
    const all = [...english, ...others.fr, ...others.ar];
    const empty = all.filter(([, text]) => text.trim() === '').map(([key]) => key);
    expect(empty).toEqual([]);
  });

  it('write Arabic texts in Arabic script (no untranslated English left in ar.json)', () => {
    // Language names, product codes and placeholders-only texts may stay Latin; everything else must contain Arabic.
    const latinAllowed = /^(language\.names\.|app\.name$|overview\.title$)/;
    const arabicScript = /\p{Script=Arabic}/u;
    const untranslated = [...others.ar]
      .filter(([key]) => !latinAllowed.test(key))
      .filter(([, text]) => /[A-Za-z]{3,}/.test(text.replace(/\{\{\s*\w+\s*\}\}/g, '')) && !arabicScript.test(text))
      .map(([key]) => key);
    expect(untranslated).toEqual([]);
  });
});
