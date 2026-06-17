type LogLevel = 'info' | 'warn' | 'error';

class Logger {
  private sensitiveKeys = ['password', 'token', 'patientId', 'email', 'amount'];

  private redact(data: any): any {
    if (typeof data !== 'object' || data === null) return data;

    if (Array.isArray(data)) {
      return data.map(item => this.redact(item));
    }

    const redacted = { ...data };
    for (const key of Object.keys(redacted)) {
      if (this.sensitiveKeys.includes(key)) {
        redacted[key] = '[REDACTED]';
      } else if (typeof redacted[key] === 'object') {
        redacted[key] = this.redact(redacted[key]);
      }
    }
    return redacted;
  }

  log(level: LogLevel, message: string, context?: any) {
    const timestamp = new Date().toISOString();
    const redactedContext = context ? this.redact(context) : '';

    // Centralized capture (simulated)
    console.log(`[${timestamp}] [${level.toUpperCase()}] ${message}`, redactedContext);

    if (level === 'error') {
      this.sendToAlertingSystem(message, redactedContext);
    }
  }

  private sendToAlertingSystem(message: string, context: any) {
    // Integration with alerting tool like Sentry or PagerDuty
    // console.log("ALERT SENT");
  }

  info(message: string, context?: any) { this.log('info', message, context); }
  warn(message: string, context?: any) { this.log('warn', message, context); }
  error(message: string, context?: any) { this.log('error', message, context); }
}

export const logger = new Logger();
