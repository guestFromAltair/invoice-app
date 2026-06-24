import { Injectable } from '@nestjs/common';
import * as bcrypt from 'bcryptjs';
import { HashingService } from './hashing.service';

@Injectable()
export class BcryptHashingService extends HashingService {
  private static readonly SALT_ROUNDS = 12;

  async hash(data: string): Promise<string> {
    const salt = await bcrypt.genSalt(BcryptHashingService.SALT_ROUNDS);
    return bcrypt.hash(data, salt);
  }

  async compare(data: string, encrypted: string): Promise<boolean> {
    return bcrypt.compare(data, encrypted);
  }
}
