import { SetMetadata } from '@nestjs/common';

export const IS_IDEMPOTENT_KEY = 'isIdempotent';

export const Idempotent = () => SetMetadata(IS_IDEMPOTENT_KEY, true);
