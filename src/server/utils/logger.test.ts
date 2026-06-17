import { logger } from './logger';

describe('Logger Redaction', () => {
  let consoleSpy: jest.SpyInstance;

  beforeEach(() => {
    consoleSpy = jest.spyOn(console, 'log').mockImplementation();
  });

  afterEach(() => {
    consoleSpy.mockRestore();
  });

  it('should redact sensitive keys in flat objects', () => {
    const context = {
      patientId: '12345',
      name: 'John Doe',
      email: 'john@example.com'
    };

    logger.info('Test message', context);

    const lastCall = consoleSpy.mock.calls[0];
    const loggedContext = lastCall[2];

    expect(loggedContext.patientId).toBe('[REDACTED]');
    expect(loggedContext.email).toBe('[REDACTED]');
    expect(loggedContext.name).toBe('John Doe');
  });

  it('should redact sensitive keys in nested objects', () => {
    const context = {
      user: {
        id: 'u1',
        token: 'secret-token'
      },
      transaction: {
        amount: 1000,
        currency: 'USD'
      }
    };

    logger.info('Nested test', context);

    const lastCall = consoleSpy.mock.calls[0];
    const loggedContext = lastCall[2];

    expect(loggedContext.user.token).toBe('[REDACTED]');
    expect(loggedContext.transaction.amount).toBe('[REDACTED]');
    expect(loggedContext.user.id).toBe('u1');
  });

  it('should not mutate original object', () => {
    const context = { patientId: '123' };
    logger.info('Mutation test', context);
    expect(context.patientId).toBe('123');
  });

  it('should preserve array structure while redacting items', () => {
    const context = [
      { patientId: 'p1', name: 'Alice' },
      { patientId: 'p2', name: 'Bob' }
    ];

    logger.info('Array test', context);

    const lastCall = consoleSpy.mock.calls[0];
    const loggedContext = lastCall[2];

    expect(Array.isArray(loggedContext)).toBe(true);
    expect(loggedContext[0].patientId).toBe('[REDACTED]');
    expect(loggedContext[1].name).toBe('Bob');
  });
});
