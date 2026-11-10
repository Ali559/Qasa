// SF Symbols used on iOS → the Lucide icon that reads the same, plus filled forms of the
// navigation icons (outline when a tab is inactive, filled when it is selected).
import { defineComponent, h, type Component } from 'vue'
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

// MARK: filled navigation icons

/** One element of a Lucide icon, as `[tag, attributes]`. */
type IconNode = [string, Record<string, string | number>]

let maskSeq = 0

/**
 * The filled form of a Lucide outline icon, for the selected tab: `body` drawn filled (with
 * the outline's own stroke, so it keeps the outline's size and rounded joins), `details`
 * cut out of it through a mask, so the inner lines read as gaps on any background.
 */
function filledIcon(name: string, body: IconNode[], details: IconNode[]): Component {
  return defineComponent({
    name,
    props: { size: { type: [Number, String], default: 24 } },
    setup(props, { attrs }) {
      const id = `qasa-icon-mask-${++maskSeq}`
      return () =>
        h(
          'svg',
          { ...attrs, xmlns: 'http://www.w3.org/2000/svg', width: props.size, height: props.size, viewBox: '0 0 24 24', fill: 'none' },
          [
            h('defs', [
              h('mask', { id, maskUnits: 'userSpaceOnUse', x: 0, y: 0, width: 24, height: 24 }, [
                h('rect', { width: 24, height: 24, fill: '#fff' }),
                h(
                  'g',
                  { fill: '#000', stroke: '#000', 'stroke-width': 1.75, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' },
                  details.map(([tag, a]) => h(tag, a)),
                ),
              ]),
            ]),
            h(
              'g',
              { mask: `url(#${id})`, fill: 'currentColor', stroke: 'currentColor', 'stroke-width': 2, 'stroke-linejoin': 'round' },
              body.map(([tag, a]) => h(tag, a)),
            ),
          ],
        )
    },
  })
}

export const HouseFilled = filledIcon(
  'HouseFilled',
  [['path', { d: 'M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' }]],
  [['path', { d: 'M15 22v-9a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v9z' }]],
)

export const KeyRoundFilled = filledIcon(
  'KeyRoundFilled',
  [['path', { d: 'M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z' }]],
  [['circle', { cx: 16.5, cy: 7.5, r: 0.75 }]],
)

export const ShieldAlertFilled = filledIcon(
  'ShieldAlertFilled',
  [['path', { d: 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z' }]],
  [
    ['path', { d: 'M12 8v4', fill: 'none' }],
    ['path', { d: 'M12 16h.01', fill: 'none' }],
  ],
)

export const CircleUserFilled = filledIcon(
  'CircleUserFilled',
  [['circle', { cx: 12, cy: 12, r: 10 }]],
  [
    ['circle', { cx: 12, cy: 10, r: 3 }],
    ['path', { d: 'M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662' }],
  ],
)
