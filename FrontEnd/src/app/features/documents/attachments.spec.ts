import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { MAX_FILE_BYTES, checkFile } from './attachments.api';
import { Attachments, formatSize } from './attachments';

const file = (name: string, size: number) => new File([new Uint8Array(size)], name);

describe('checkFile', () => {
  it('accepts allowed types within the size limit', () => {
    expect(checkFile(file('datasheet.pdf', 10), 0)).toBeNull();
    expect(checkFile(file('Photo.JPG', 10), 0)).toBeNull();
  });

  it('refuses scripts, web pages and SVG, empty and oversized files, and too many files', () => {
    expect(checkFile(file('setup.exe', 10), 0)).toBe('file-type-not-allowed');
    expect(checkFile(file('page.html', 10), 0)).toBe('file-type-not-allowed');
    expect(checkFile(file('logo.svg', 10), 0)).toBe('file-type-not-allowed');
    expect(checkFile(file('macro.xlsm', 10), 0)).toBe('file-type-not-allowed');
    expect(checkFile(file('legacy.doc', 10), 0)).toBe('file-type-not-allowed');
    expect(checkFile(file('empty.pdf', 0), 0)).toBe('file-empty');
    expect(checkFile({ name: 'big.pdf', size: MAX_FILE_BYTES + 1 } as File, 0)).toBe('file-too-large');
    expect(checkFile(file('one-more.pdf', 10), 30)).toBe('too-many-files');
  });

  it('formats sizes in the display locale', () => {
    expect(formatSize(1536, 'en-GB')).toBe('1.5 KB');
    expect(formatSize(1536, 'fr-FR')).toBe('1,5 KB');
  });
});

describe('Attachments', () => {
  let fixture: ComponentFixture<Attachments>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;

  async function render(editable: boolean) {
    TestBed.configureTestingModule({
      imports: [Attachments],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Attachments);
    fixture.componentRef.setInput('requestId', 7);
    fixture.componentRef.setInput('editable', editable);
    await fixture.whenStable();
    http.expectOne('/api/v1/requests/7/attachments').flush([
      { id: 1, fileName: 'fiche-technique.pdf', sizeBytes: 2048, uploadedAt: '2026-11-02T08:00:00Z', scanStatus: 'CLEAN', downloadUrl: '/api/v1/attachments/1/content', canDelete: false },
      { id: 2, fileName: 'photo.jpg', sizeBytes: 4096, uploadedAt: '2026-11-02T08:00:00Z', scanStatus: 'PENDING', canDelete: true },
    ]);
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  it('offers a download only for files the virus check reported clean', async () => {
    await render(false);
    const links = element().querySelectorAll<HTMLAnchorElement>('a.file-name');
    expect(links.length).toBe(1);
    expect(links[0].getAttribute('href')).toBe('/api/v1/attachments/1/content');
    expect(element().textContent).toContain('attachments.scanning');
  });

  it('hides upload and remove when the request is no longer editable', async () => {
    await render(false);
    expect(element().querySelector('input[type="file"]')).toBeNull();
    expect(element().querySelector('.icon-button')).toBeNull();
  });

  it('offers removal only for files the server says the user may delete', async () => {
    await render(true);
    expect(element().querySelectorAll('.icon-button').length).toBe(1);
  });

  it('refuses a disallowed file before uploading it', async () => {
    await render(true);
    const input = element().querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file('virus.exe', 10)] });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    http.expectNone('/api/v1/requests/7/attachments');
    expect(element().querySelector('[role="alert"]')?.textContent).toContain('attachments.errors.file-type-not-allowed');
  });
});
