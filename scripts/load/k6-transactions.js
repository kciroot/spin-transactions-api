// Load test for POST /transactions (+ a few GETs). Run with: make load PROFILE=avg|peak
// Capacity model: ~58 tx/s average, ~580 tx/s peak for 5M tx/day.
// Start the app with a realistic provider latency first:  PROVIDER_MOCK_LATENCY_MS=300 make up
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'dev-key-change-me';
const PROFILE = __ENV.PROFILE || 'avg';
const DURATION = __ENV.DURATION || '2m';

const avg = {
  executor: 'constant-arrival-rate',
  rate: parseInt(__ENV.RATE || '58'),
  timeUnit: '1s',
  duration: DURATION,
  preAllocatedVUs: 100,
  maxVUs: 2000,
};
const peak = {
  executor: 'ramping-arrival-rate',
  startRate: 58,
  timeUnit: '1s',
  preAllocatedVUs: 200,
  maxVUs: 4000,
  stages: [
    { target: parseInt(__ENV.RATE || '580'), duration: '1m' }, // ramp to peak
    { target: parseInt(__ENV.RATE || '580'), duration: DURATION }, // sustain
    { target: 58, duration: '30s' }, // ramp down
  ],
};

export const options = {
  scenarios: { load: PROFILE === 'peak' ? peak : avg },
  thresholds: {
    http_req_failed: ['rate<0.01'],          // < 1% transport/HTTP errors
    http_req_duration: ['p(95)<1000'],       // p95 under 1s (provider latency + overhead)
    checks: ['rate>0.99'],
  },
};

const headers = { 'Content-Type': 'application/json', 'X-API-Key': API_KEY };

function rid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
}

export default function () {
  const id = rid();
  const isDebit = Math.random() < 0.2;
  const body = JSON.stringify({
    accountId: `acc-load-${id}`,
    type: isDebit ? 'DEBIT' : 'CREDIT',
    amount: isDebit ? 100.0 : 250.0,
    currency: 'MXN',
    description: 'load test',
  });
  const res = http.post(`${BASE_URL}/transactions`, body, {
    headers: Object.assign({ 'Idempotency-Key': id }, headers),
  });
  check(res, {
    'status is 201': (r) => r.status === 201,
    'has status field': (r) => r.json('status') !== undefined,
  });

  if (Math.random() < 0.05) {
    const list = http.get(`${BASE_URL}/transactions?limit=20&status=EXECUTED`, { headers });
    check(list, { 'list is 200': (r) => r.status === 200 });
  }
}
