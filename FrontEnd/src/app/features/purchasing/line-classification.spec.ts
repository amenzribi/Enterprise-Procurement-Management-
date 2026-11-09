import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PurchaseRequest } from '../requests/request.models';
import { LineClassification } from './line-classification';

const LINES = [
  { id: 1, description: 'Pump overhaul', lineKind: 'SERVICE', serviceConfirmed: false },
  { id: 2, description: 'Bearings', lineKind: 'SERVICE', serviceConfirmed: false },
  { id: 3, description: 'Seal', lineKind: 'GOODS', serviceConfirmed: false },
];

describe('LineClassification', () => {
  let fixture: ComponentFixture<LineClassification>;
  let http: HttpTestingController;
  const me = signal<unknown>({ id: 7, permissions: ['PURCHASING_ACT'] });
  const element = () => fixture.nativeElement as HTMLElement;
  const open = (changes: Partial<PurchaseRequest> = {}) => {
    fixture.componentRef.setInput('request', { id: 42, version: 3, status: 'ASSIGNED_TO_BUYER', requester: { id: 9 }, enteredBy: { id: 9 }, lines: LINES, ...changes } as unknown as PurchaseRequest);
    fixture.detectChanges();
  };

  beforeEach(() => {
    me.set({ id: 7, permissions: ['PURCHASING_ACT'] });
    TestBed.configureTestingModule({
      imports: [LineClassification],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService(), { provide: AuthService, useValue: { me } }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LineClassification);
  });

  it('lists only unconfirmed service lines and sends those marked as goods', () => {
    open();
    expect(element().querySelectorAll('li')).toHaveLength(2);
    element().querySelector<HTMLInputElement>('#classify-goods-2')!.click();
    fixture.detectChanges();
    element().querySelector<HTMLButtonElement>('button')!.click();
    expect(http.expectOne('/api/v1/purchasing/requests/42/line-classification').request.body).toEqual({ version: 3, reclassifyAsGoods: [2] });
  });

  it('is not offered to the author of the request', () => {
    me.set({ id: 9, permissions: ['PURCHASING_ACT'] });
    open();
    expect(element().querySelector('section')).toBeNull();
  });

  it('is not offered after award', () => {
    open({ status: 'AWARDED' } as Partial<PurchaseRequest>);
    expect(element().querySelector('section')).toBeNull();
  });
});
