import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  Activity,
  ArrowRight,
  Ban,
  Bell,
  CalendarDays,
  CheckCheck,
  Coins,
  Download,
  Forward,
  Hourglass,
  Mail,
  MessagesSquare,
  UserPlus,
  Moon,
  Sun,
  MessageCircleQuestionMark,
  Paperclip,
  Reply,
  Settings,
  Undo2,
  Upload,
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  ArrowUpDown,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  LogIn,
  LogOut,
  Pencil,
  Plus,
  Save,
  Search,
  Send,
  Trash2,
  UserRound,
  Info,
  History,
  LockKeyhole,
  Building2,
  Check,
  CircleCheck,
  CircleX,
  Clock,
  Database,
  FilePlus,
  FileText,
  Globe,
  House,
  type IconNode,
  Inbox,
  LayoutDashboard,
  Menu,
  Monitor,
  PackageCheck,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCw,
  ClipboardCopy,
  Scale,
  Server,
  TriangleAlert,
  Truck,
  Users,
  X,
} from 'lucide';

// Icons from Lucide (ISC licence). Only the icons listed here are bundled.
const ICONS = {
  activity: Activity,
  'arrow-right': ArrowRight,
  ban: Ban,
  bell: Bell,
  calendar: CalendarDays,
  'check-check': CheckCheck,
  coins: Coins,
  download: Download,
  forward: Forward,
  hourglass: Hourglass,
  mail: Mail,
  messages: MessagesSquare,
  'user-plus': UserPlus,
  moon: Moon,
  sun: Sun,
  question: MessageCircleQuestionMark,
  paperclip: Paperclip,
  reply: Reply,
  settings: Settings,
  undo: Undo2,
  upload: Upload,
  'arrow-down': ArrowDown,
  'arrow-left': ArrowLeft,
  'arrow-up': ArrowUp,
  'arrow-up-down': ArrowUpDown,
  'chevron-down': ChevronDown,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  'eye': Eye,
  'eye-off': EyeOff,
  'log-in': LogIn,
  'log-out': LogOut,
  'pencil': Pencil,
  'plus': Plus,
  'save': Save,
  'search': Search,
  'send': Send,
  'trash': Trash2,
  'user': UserRound,
  'info': Info,
  'history': History,
  'lock': LockKeyhole,
  building: Building2,
  check: Check,
  copy: ClipboardCopy,
  'circle-check': CircleCheck,
  'circle-x': CircleX,
  clock: Clock,
  dashboard: LayoutDashboard,
  database: Database,
  'file-plus': FilePlus,
  'file-text': FileText,
  globe: Globe,
  house: House,
  inbox: Inbox,
  menu: Menu,
  monitor: Monitor,
  'package-check': PackageCheck,
  'panel-left-close': PanelLeftClose,
  'panel-left-open': PanelLeftOpen,
  refresh: RefreshCw,
  scale: Scale,
  server: Server,
  'triangle-alert': TriangleAlert,
  truck: Truck,
  users: Users,
  x: X,
} satisfies Record<string, IconNode>;

export type IconName = keyof typeof ICONS;

/** Icons that point in the reading direction; they are mirrored in right-to-left languages (S2-AC-01). */
const DIRECTIONAL: ReadonlySet<IconName> = new Set<IconName>([
  'arrow-left',
  'arrow-right',
  'chevron-left',
  'chevron-right',
  'panel-left-close',
  'panel-left-open',
  'send',
  'forward',
  'reply',
  'undo',
  'log-in',
  'log-out',
]);

/**
 * Decorative SVG icon. Always pair it with visible text or an accessible label on the parent control.
 */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'app-icon' },
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      focusable="false"
      [class.is-directional]="directional()"
      [attr.width]="size()"
      [attr.height]="size()"
      [attr.stroke-width]="strokeWidth()"
    >
      @for (node of nodes(); track $index) {
        @switch (node[0]) {
          @case ('path') { <svg:path [attr.d]="node[1]['d']" /> }
          @case ('circle') { <svg:circle [attr.cx]="node[1]['cx']" [attr.cy]="node[1]['cy']" [attr.r]="node[1]['r']" /> }
          @case ('line') { <svg:line [attr.x1]="node[1]['x1']" [attr.y1]="node[1]['y1']" [attr.x2]="node[1]['x2']" [attr.y2]="node[1]['y2']" /> }
          @case ('rect') {
            <svg:rect [attr.x]="node[1]['x']" [attr.y]="node[1]['y']" [attr.width]="node[1]['width']"
              [attr.height]="node[1]['height']" [attr.rx]="node[1]['rx']" [attr.ry]="node[1]['ry']" />
          }
          @case ('polyline') { <svg:polyline [attr.points]="node[1]['points']" /> }
          @case ('polygon') { <svg:polygon [attr.points]="node[1]['points']" /> }
          @case ('ellipse') { <svg:ellipse [attr.cx]="node[1]['cx']" [attr.cy]="node[1]['cy']" [attr.rx]="node[1]['rx']" [attr.ry]="node[1]['ry']" /> }
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-shrink: 0;
      line-height: 0;
    }

    :host-context([dir='rtl']) .is-directional {
      transform: scaleX(-1);
    }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(18);
  readonly strokeWidth = input(1.75);

  protected readonly directional = computed(() => DIRECTIONAL.has(this.name()));
  protected readonly nodes = computed(() => ICONS[this.name()] as unknown as [string, Record<string, string>][]);
}
