import request from 'supertest';
import { bootstrapE2E, teardownE2E, E2EContext } from './utils/e2e-setup';

describe('Auth (e2e)', () => {
  let ctx: E2EContext;

  const credentials = {
    email: 'e2e@invoiceapp.com',
    password: 'password123',
  };

  const api = () => request(ctx.app.getHttpServer());

  beforeAll(async () => {
    ctx = await bootstrapE2E();
  }, 120_000);

  afterAll(async () => {
    await teardownE2E(ctx);
  });

  it('POST /api/auth/register -> 201 and returns an accessToken', async () => {
    const res = await api().post('/api/auth/register').send(credentials).expect(201);

    expect(res.body.accessToken).toEqual(expect.any(String));
  });

  it('POST /api/auth/register with a duplicate email -> 409 Conflict', async () => {
    await api().post('/api/auth/register').send(credentials).expect(409);
  });

  it('POST /api/auth/login -> 200 and returns an accessToken', async () => {
    const res = await api().post('/api/auth/login').send(credentials).expect(200);

    expect(res.body.accessToken).toEqual(expect.any(String));
  });

  it('POST /api/auth/login with a wrong password -> 401 Unauthorized', async () => {
    await api()
      .post('/api/auth/login')
      .send({ ...credentials, password: 'wrong-password' })
      .expect(401);
  });

  it('POST /api/auth/register with an invalid body -> 400 with a message array', async () => {
    const res = await api().post('/api/auth/register').send({ email: 'not-an-email', password: 'short' }).expect(400);

    expect(Array.isArray(res.body.message)).toBe(true);
    expect(res.body.message.length).toBeGreaterThan(0);
  });

  it('GET /api/auth/me without a token -> 401 Unauthorized', async () => {
    await api().get('/api/auth/me').expect(401);
  });

  it('GET /api/auth/me with a valid token -> 200 and the current user', async () => {
    const login = await api().post('/api/auth/login').send(credentials).expect(200);
    const token = login.body.accessToken as string;

    const res = await api().get('/api/auth/me').set('Authorization', `Bearer ${token}`).expect(200);

    expect(res.body).toMatchObject({
      email: credentials.email,
      role: 'USER',
    });
    expect(res.body.id).toEqual(expect.any(String));
  });
});
