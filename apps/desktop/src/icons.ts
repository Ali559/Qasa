// SF Symbols used on iOS → the Lucide icon that reads the same.
import {
  Briefcase,
  Car,
  CreditCard,
  Earth,
  FileText,
  Globe,
  IdCard,
  Landmark,
  Link,
  Mail,
  MapPin,
  Phone,
  User,
  type LucideIcon,
} from 'lucide-vue-next'

import type { Variant } from './vault/types'

/** iOS `VaultPayload.systemImage`. */
export const VARIANT_ICON: Record<Variant, LucideIcon> = {
  login: Globe,
  secureNote: FileText,
  card: CreditCard,
  bankAccount: Landmark,
  name: User,
  personalEmail: Mail,
  phone: Phone,
  address: MapPin,
  company: Briefcase,
  website: Link,
  idCard: IdCard,
  passport: Earth,
  driverLicense: Car,
}
