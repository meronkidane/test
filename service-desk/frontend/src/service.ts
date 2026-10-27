import {inject, Injectable, InjectionToken} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import Keycloak from 'keycloak-js';
export const SESSION = new InjectionToken<Keycloak>('session');
export const SETTINGS = new InjectionToken<{apiBase:string}>('settings');
export type Status = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export type Priority = 'LOW' | 'NORMAL' | 'HIGH';
export interface Item {id:string; title:string; description:string; priority:Priority; status:Status; requester:string; version:number; createdAt:string; updatedAt:string}
export interface Page {items:Item[]; page:number; size:number; hasNext:boolean}
@Injectable({providedIn:'root'})
export class Desk {
 readonly session = inject(SESSION);
 private http = inject(HttpClient);
 private base = inject(SETTINGS).apiBase;
 async request<T>(method:string,path:string,body?:unknown):Promise<T> {
  await this.session.updateToken(30);
  if (!this.session.token) throw new Error('Session expired');
  return firstValueFrom(this.http.request<T>(method,this.base+path,{body,headers:{Authorization:'Bearer '+this.session.token}}));
 }
 list(page:number,status:string) { return this.request<Page>('GET',`/requests?page=${page}&size=10${status ? '&status='+encodeURIComponent(status) : ''}`); }
 create(input:{title:string;description:string;priority:Priority}) { return this.request<Item>('POST','/requests',input); }
 transition(item:Item,status:Status) { return this.request<Item>('PATCH',`/requests/${item.id}/status`,{status,version:item.version}); }
}
