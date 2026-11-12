import { Dialog } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, signal, viewChild } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { openConfirm } from '../../shared/ui/confirm-dialog';
import { Icon } from '../../shared/ui/icon';
import { ACCEPTED_TYPES, Attachment, AttachmentsApi, MAX_FILE_BYTES, checkFile } from './attachments.api';

type ListState = 'loading' | 'ready' | 'error';

/** Human-readable file size ("1.2 MB") in the current locale. Exported for tests. */
export function formatSize(bytes: number, locale: string): string {
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: unit === 0 ? 0 : 1 }).format(value)} ${units[unit]}`;
}

/**
 * Files of a request (datasheets, photos, quotations): list with download for everyone who can see the request, and
 * upload/remove while the request is editable (draft or returned for revision) (S2-AC-12). File names come from users
 * and are always rendered as text, isolated for bidirectional display.
 */
@Component({
  selector: 'app-attachments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, Icon, TranslatePipe],
  template: `
    <section class="panel" aria-labelledby="attachments-title">
      <h2 class="panel-title" id="attachments-title">
        <app-icon name="paperclip" [size]="16" />
        {{ 'attachments.title' | translate }}
        @if (state() === 'ready') {
          <span class="count tabular">({{ attachments().length }})</span>
        }
      </h2>

      @if (editable()) {
        <div class="upload">
          <input
            #fileInput
            id="attachment-file"
            type="file"
            class="visually-hidden"
            [accept]="accepted"
            (change)="onFileSelected($event)"
          />
          <label for="attachment-file" class="upload-button" [class.is-busy]="uploading()">
            <app-icon name="upload" [size]="16" />
            {{ (uploading() ? 'attachments.uploading' : 'attachments.add') | translate }}
          </label>
          <p class="hint">{{ 'attachments.hint' | translate: { max: maxMegabytes } }}</p>
          @if (progress() !== null) {
            <progress class="progress" max="100" [value]="progress()" [attr.aria-label]="'attachments.uploading' | translate"></progress>
          }
        </div>
      }

      <p class="visually-hidden" role="status" aria-live="polite">{{ announcement() }}</p>
      @if (error(); as code) {
        <p class="error" role="alert">{{ 'attachments.errors.' + code | translate }}</p>
      }

      @switch (state()) {
        @case ('loading') {
          <p class="muted">{{ 'attachments.loading' | translate }}</p>
        }
        @case ('error') {
          <p class="muted">
            {{ 'attachments.loadError' | translate }}
            <button appButton variant="ghost" size="small" type="button" (click)="load()">{{ 'common.retry' | translate }}</button>
          </p>
        }
        @default {
          @if (attachments().length === 0) {
            <p class="muted">{{ 'attachments.empty' | translate }}</p>
          } @else {
            <ul class="files">
              @for (file of attachments(); track file.id) {
                <li class="file">
                  <app-icon name="file-text" [size]="18" class="file-icon" />
                  <div class="file-main">
                    @if (!downloadable(file)) {
                      <bdi class="file-name">{{ file.fileName }}</bdi>
                    } @else {
                      <a class="file-name" [href]="url(file)" download><bdi>{{ file.fileName }}</bdi></a>
                    }
                    <span class="file-meta">
                      <bdi class="tabular">{{ size(file.sizeBytes) }}</bdi>
                      @if ((file.version ?? 1) > 1) {
                        · {{ 'attachments.version' | translate: { version: file.version } }}
                      }
                      · <time [attr.datetime]="file.uploadedAt">{{ file.uploadedAt | date: 'short' : undefined : language.locale() }}</time>
                      @if (file.uploadedBy) {
                        · {{ file.uploadedBy.displayName }}
                      }
                      @if (file.scanStatus === 'PENDING') {
                        · {{ 'attachments.scanning' | translate }}
                      } @else if (file.scanStatus === 'INFECTED' || file.scanStatus === 'REJECTED') {
                        · {{ 'attachments.infected' | translate }}
                      }
                    </span>
                  </div>
                  @if (editable() && file.canDelete !== false) {
                    <button
                      type="button"
                      class="icon-button"
                      [attr.aria-label]="'attachments.remove' | translate: { name: file.fileName }"
                      (click)="remove(file)"
                    >
                      <app-icon name="trash" [size]="16" />
                    </button>
                  }
                </li>
              }
            </ul>
          }
        }
      }
    </section>
  `,
  styles: `
    .panel-title {
      display: flex;
      align-items: center;
      gap: var(--space-2);
    }

    .count {
      color: var(--color-text-muted);
      font-weight: var(--weight-regular);
    }

    .upload {
      display: grid;
      gap: var(--space-2);
      margin-block: var(--space-3);
    }

    .upload-button {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      justify-self: start;
      min-block-size: 2.75rem;
      padding-inline: var(--space-4);
      border: 1px dashed var(--color-accent);
      border-radius: var(--radius-md);
      background: var(--color-accent-soft);
      color: var(--color-accent-hover);
      font-weight: var(--weight-semibold);
      cursor: pointer;
      transition: background-color var(--duration-fast) var(--ease-standard);
    }
    .upload-button:hover {
      background: color-mix(in srgb, var(--color-accent-soft) 70%, var(--color-surface));
    }

    input:focus-visible + .upload-button {
      outline: 2px solid var(--color-focus);
      outline-offset: 2px;
    }

    .upload-button.is-busy {
      pointer-events: none;
      opacity: 0.7;
    }

    .hint,
    .muted,
    .file-meta {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }

    .progress {
      inline-size: 100%;
      max-inline-size: 20rem;
      block-size: 0.5rem;
      accent-color: var(--color-accent);
    }

    .error {
      color: var(--color-danger);
      font-size: var(--text-sm);
    }

    .files {
      display: grid;
      gap: var(--space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .file {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-md);
      background: var(--color-paper);
    }

    .file-icon {
      display: grid;
      place-items: center;
      flex-shrink: 0;
      inline-size: 2rem;
      block-size: 2rem;
      border-radius: var(--radius-sm);
      background: var(--color-surface);
      color: var(--color-accent);
    }

    .file-main {
      display: grid;
      flex: 1;
      min-inline-size: 0;
    }

    .file-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-weight: var(--weight-medium);
    }

    .icon-button {
      display: inline-grid;
      place-items: center;
      inline-size: 2.5rem;
      block-size: 2.5rem;
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      color: var(--color-text-muted);
      cursor: pointer;
    }

    .icon-button:hover {
      color: var(--color-danger);
      background: var(--color-surface-sunken);
    }
  `,
})
export class Attachments {
  private readonly api = inject(AttachmentsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifications = inject(NotificationService);
  protected readonly language = inject(LanguageService);

  readonly requestId = input.required<number>();
  /** True while the request is a draft or returned for revision and the user may edit it. */
  readonly editable = input(false);

  protected readonly accepted = ACCEPTED_TYPES;
  protected readonly maxMegabytes = MAX_FILE_BYTES / (1024 * 1024);
  protected readonly state = signal<ListState>('loading');
  protected readonly attachments = signal<Attachment[]>([]);
  protected readonly uploading = signal(false);
  protected readonly progress = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly announcement = signal('');

  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  constructor() {
    effect(() => {
      if (this.requestId()) {
        this.load();
      }
    });
  }

  load(): void {
    this.state.set('loading');
    this.api.list(this.requestId()).subscribe({
      next: (attachments) => {
        this.attachments.set(attachments);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const problem = checkFile(file, this.attachments().length);
    this.error.set(problem);
    if (problem) {
      return;
    }
    this.uploading.set(true);
    this.progress.set(0);
    this.api.upload(this.requestId(), file, null).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.progress.set(Math.round((100 * event.loaded) / event.total));
        } else if (event.type === HttpEventType.Response && event.body) {
          this.attachments.update((files) => [...files, event.body!]);
          this.announcement.set(file.name);
          this.notifications.success('attachments.uploaded', { name: file.name });
        }
      },
      error: (error: unknown) => {
        this.finishUpload();
        const problem = toApiProblem(error);
        if (problem.status !== 401) {
          this.error.set(KNOWN_ERRORS.has(problem.code) ? problem.code : 'upload-failed');
        }
      },
      complete: () => this.finishUpload(),
    });
  }

  protected remove(file: Attachment): void {
    openConfirm(this.dialog, {
      titleKey: 'attachments.removeTitle',
      messageKey: 'attachments.removeMessage',
      confirmKey: 'attachments.removeConfirm',
      params: { name: file.fileName },
      tone: 'danger',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.remove(file.id).subscribe({
        next: () => {
          this.attachments.update((files) => files.filter((item) => item.id !== file.id));
          this.notifications.success('attachments.removed', { name: file.fileName });
        },
        error: (error: unknown) => {
          const problem = toApiProblem(error);
          if (problem.status !== 401) {
            this.notifications.error(`errors.${problem.code}`);
          }
        },
      });
    });
  }

  protected url(file: Attachment): string {
    return this.api.contentUrl(file);
  }

  protected downloadable(file: Attachment): boolean {
    return !file.scanStatus || file.scanStatus === 'CLEAN';
  }

  protected size(bytes: number): string {
    return formatSize(bytes, this.language.locale());
  }

  private finishUpload(): void {
    this.uploading.set(false);
    this.progress.set(null);
    this.fileInput()?.nativeElement.blur();
  }
}

const KNOWN_ERRORS = new Set([
  'file-too-large',
  'file-type-not-allowed',
  'too-many-files',
  'attachment-limit-reached',
  'file-infected',
  'file-empty',
  'invalid-state',
  'forbidden',
]);
