export interface ClientResponse {
  id: string;
  ownerId: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  vatNumber: string | null;
  version: number;
  createdAt: Date;
  updatedAt: Date;
}
