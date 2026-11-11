// Which library icon (icons/qasaIcons.ts, design/README.md "Icons") each item type shows:
// the iOS `VaultPayload.systemImage`, through the SF Symbol each icon replaced.
import type { QasaIconName } from './icons/qasaIcons'
import type { Variant } from './vault/types'

/** iOS `VaultPayload.systemImage`. */
export const VARIANT_ICON: Record<Variant, QasaIconName> = {
  login: 'login',
  secureNote: 'note',
  card: 'card',
  bankAccount: 'bank',
  name: 'person',
  personalEmail: 'email',
  phone: 'phone',
  address: 'address',
  company: 'work',
  website: 'link',
  idCard: 'identity',
  passport: 'location',
  driverLicense: 'car',
}
