import {test} from 'node:test';
import assert from 'node:assert/strict';
import {canManageRoles} from '../src/roles.ts';

test('malformed or absent role claims fail closed without throwing', () => {
  for (const value of [undefined, null, 'ADMIN', 1, {}, {some: true}, ['ADMIN', 3], ['LEAD', null], [['ADMIN']]]) {
    assert.equal(canManageRoles(value), false);
  }
});
test('only exact supported management roles enable the UI', () => {
  for (const roles of [[], ['AUTHOR'], ['admin'], ['SUPERADMIN'], [' LEAD ']]) {
    assert.equal(canManageRoles(roles), false);
  }
  for (const roles of [['LEAD'], ['ADMIN'], ['AUTHOR', 'LEAD']]) {
    assert.equal(canManageRoles(roles), true);
  }
});
