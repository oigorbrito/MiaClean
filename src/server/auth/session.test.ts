import { getSession } from './session';
import { Request } from 'express';

describe('Session Management', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...originalEnv };
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  it('should throw error in production when session is missing', () => {
    process.env.NODE_ENV = 'production';
    process.env.USE_DEMO = 'true'; // Should be ignored
    const req = { session: null } as unknown as Request;

    expect(() => getSession(req)).toThrow("Unauthorized: Active session required in production.");
  });

  it('should return session in production when present', () => {
    process.env.NODE_ENV = 'production';
    const mockSession = { patientId: 'real-123' };
    const req = { session: mockSession } as unknown as Request;

    expect(getSession(req)).toBe(mockSession);
  });

  it('should allow demo fallback in non-production when USE_DEMO is true', () => {
    process.env.NODE_ENV = 'development';
    process.env.USE_DEMO = 'true';
    const req = { session: null } as unknown as Request;

    const session = getSession(req);
    expect(session).toHaveProperty('patientId');
    expect(session?.patientId).toContain('demo-patient');
  });

  it('should not allow demo fallback in non-production when USE_DEMO is false', () => {
    process.env.NODE_ENV = 'development';
    process.env.USE_DEMO = 'false';
    const req = { session: null } as unknown as Request;

    expect(getSession(req)).toBeNull();
  });
});
