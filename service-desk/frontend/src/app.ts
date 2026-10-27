import {Component, inject, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {Desk, Item, Page, Priority, Status} from './service';
@Component({selector:'desk-app',imports:[ReactiveFormsModule,DatePipe],templateUrl:'./app.html'})
export class App {
 readonly desk = inject(Desk);
 readonly user = this.desk.session.tokenParsed?.['preferred_username'] || 'Team member';
 readonly tenant = this.desk.session.tokenParsed?.['tenant'] || '';
 readonly canManage = (this.desk.session.tokenParsed?.['roles'] as string[] || []).some(r => ['LEAD','ADMIN'].includes(r));
 readonly result = signal<Page>({items:[],page:0,size:10,hasNext:false});
 readonly selected = signal<Item|null>(null);
 readonly busy = signal(false);
 readonly error = signal('');
 readonly notice = signal('');
 readonly filter = signal('');
 readonly form = inject(FormBuilder).nonNullable.group({
  title:['',[Validators.required,Validators.maxLength(160),Validators.pattern(/\S/)]],
  description:['',[Validators.required,Validators.maxLength(4000),Validators.pattern(/\S/)]],
  priority:['NORMAL' as Priority,Validators.required]
 });
 constructor() { void this.load(0); }
 async action(work:()=>Promise<void>) {
  if(this.busy()) return;
  this.busy.set(true); this.error.set(''); this.notice.set('');
  try { await work(); }
  catch(e) {
   const status = e instanceof HttpErrorResponse ? e.status : 0;
   this.error.set(status===409 ? 'This request changed or its transition is no longer valid. Refresh the queue before trying again.' :
    status===403 ? 'Your role does not allow this action.' : status===401 ? 'Your session expired. Sign out and sign in again.' :
    status===400 ? 'Check the request fields and try again.' : 'The service could not complete the action. Refresh to check whether it was saved before submitting again.');
  } finally { this.busy.set(false); }
 }
 async load(page:number,filter=this.filter()) {
  await this.action(async()=>{
   const data=await this.desk.list(page,filter); this.result.set(data); this.filter.set(filter);
   const id=this.selected()?.id; this.selected.set(data.items.find(i=>i.id===id) || null);
  });
 }
 async create() {
  this.form.markAllAsTouched(); if(this.form.invalid) return;
  await this.action(async()=>{
   const item=await this.desk.create(this.form.getRawValue());
   this.form.reset({title:'',description:'',priority:'NORMAL'});
   this.selected.set(item); this.notice.set('Request created.');
   // Preserve success if refreshing fails; do not ask the user to repeat a successful POST.
   try { this.result.set(await this.desk.list(0,'')); this.filter.set(''); }
   catch { this.notice.set('Request created. Refresh the queue to retrieve the latest list.'); }
  });
 }
 next(status:Status):Status { return status==='OPEN'?'IN_PROGRESS':status==='IN_PROGRESS'?'RESOLVED':'OPEN'; }
 label(status:Status) { return status==='OPEN'?'Open':status==='IN_PROGRESS'?'In progress':'Resolved'; }
 async advance(item:Item) {
  await this.action(async()=>{
   const updated=await this.desk.transition(item,this.next(item.status)); this.selected.set(updated);
   this.result.update(p=>({...p,items:p.items.map(i=>i.id===updated.id?updated:i).filter(i=>!this.filter()||i.status===this.filter())}));
   this.notice.set('Request moved to '+this.label(updated.status)+'.');
  });
 }
 logout() { void this.desk.session.logout({redirectUri:window.location.origin}); }
}
