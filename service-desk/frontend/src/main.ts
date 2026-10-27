import {bootstrapApplication} from '@angular/platform-browser';
import {provideHttpClient} from '@angular/common/http';
import Keycloak from 'keycloak-js';
import {App} from './app';
import {SESSION, SETTINGS} from './service';

async function start() {
 const response = await fetch('/config.json');
 if (!response.ok) throw new Error('Configuration unavailable');
 const settings = await response.json();
 const session = new Keycloak(settings.oidc);
 await session.init({onLoad:'login-required', pkceMethod:'S256', checkLoginIframe:false});
 await bootstrapApplication(App, {providers:[provideHttpClient(), {provide:SESSION,useValue:session}, {provide:SETTINGS,useValue:settings}]});
}
start().catch(() => { document.body.textContent = 'Unable to start Fieldwork. Check the login service, then reload this page.'; });
