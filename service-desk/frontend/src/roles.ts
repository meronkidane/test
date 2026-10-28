/** Presentation hint only. The API validates JWTs and authorizes every mutation. */
export function canManageRoles(value: unknown): boolean {
  return Array.isArray(value)
    && value.every((role: unknown) => typeof role === 'string')
    && value.some(role => role === 'LEAD' || role === 'ADMIN');
}
