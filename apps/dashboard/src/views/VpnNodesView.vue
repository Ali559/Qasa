<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Activity, Copy, Gauge, Plus, Timer, UsersRound } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import StatTile from '@/components/kit/StatTile.vue'
import InkPanel from '@/components/kit/InkPanel.vue'
import NodeCard from '@/components/vpn/NodeCard.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { vpnKpis as mockVpnKpis, vpnNodes as mockVpnNodes } from '@/data/mock'
import {
  createVpnNode,
  fetchVpnNodes,
  retireVpnNode,
  rotateVpnAgentToken,
  updateVpnNode,
  type VpnNodeRow,
} from '@/api/admin'
import { canWrite, errorMessage, isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const live = isLiveApiEnabled()
const vpnKpis = ref([...mockVpnKpis])
const vpnNodes = ref<VpnNodeRow[]>(mockVpnNodes.map((n, i) => ({ ...n, id: `demo-node-${i + 1}` })))
const busy = ref(false)

async function load() {
  if (!live) return
  try {
    const data = await fetchVpnNodes()
    vpnKpis.value = data.kpis
    vpnNodes.value = data.nodes
  } catch (err) {
    toast.error('Failed to load VPN nodes', { description: errorMessage(err) })
  }
}

onMounted(load)

const kpiStyle = [
  { icon: UsersRound, accent: 'var(--chart-2)' },
  { icon: Gauge, accent: 'var(--chart-4)' },
  { icon: Timer, accent: 'var(--chart-3)' },
  { icon: Activity, accent: 'var(--chart-5)' },
]
const styleFor = (i: number) => kpiStyle[i % kpiStyle.length]!

// Retired nodes sink to the end.
const sortedNodes = computed(() =>
  [...vpnNodes.value].sort((a, b) => Number(a.status === 'retired') - Number(b.status === 'retired')),
)

// --- Create --------------------------------------------------------------

const createOpen = ref(false)
const blank = () => ({
  name: '',
  city: '',
  public_ip: '',
  wg_public_key: '',
  ip_pool_cidr: '10.8.0.0/16',
  endpoint_port: '51820',
  max_peers: '1000',
  service_name: 'wg-main',
})
const form = reactive(blank())

// The agent token is returned once (create or rotate); show it until dismissed.
const token = ref<{ node: string; value: string } | null>(null)

async function addNode() {
  if (!form.name.trim() || !form.city.trim() || !form.public_ip.trim() || !form.wg_public_key.trim()) {
    toast.error('Name, city, public IP and WireGuard key are required')
    return
  }
  busy.value = true
  try {
    if (live) {
      const res = await createVpnNode({
        name: form.name.trim(),
        city: form.city.trim(),
        public_ip: form.public_ip.trim(),
        wg_public_key: form.wg_public_key.trim(),
        ip_pool_cidr: form.ip_pool_cidr.trim(),
        endpoint_port: Number(form.endpoint_port) || undefined,
        max_peers: Number(form.max_peers) || undefined,
        service_name: form.service_name.trim() || undefined,
      })
      token.value = { node: res.node.name, value: res.agent_token }
      await load()
    } else {
      vpnNodes.value.push({
        id: `demo-node-${Date.now()}`,
        name: form.name,
        city: form.city,
        status: 'draining',
        ip: form.public_ip,
        service: form.service_name,
        cpu: 0,
        memory: 0,
        memoryLabel: '0%',
        bandwidth: '0 Mbps',
        peers: '0',
        maxPeers: Number(form.max_peers) || 1000,
        endpointPort: Number(form.endpoint_port) || 51820,
        lastHeartbeat: null,
      })
      token.value = { node: form.name, value: 'demo-token-not-real-0123456789abcdef' }
    }
    createOpen.value = false
    Object.assign(form, blank())
  } catch (err) {
    toast.error('Node not added', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

function copyToken() {
  if (!token.value) return
  navigator.clipboard.writeText(token.value.value)
  toast.success('Agent token copied')
}

// --- Edit ----------------------------------------------------------------

const editTarget = ref<VpnNodeRow | null>(null)
const edit = reactive({ name: '', city: '', max_peers: '', endpoint_port: '', wg_public_key: '' })

function openEdit(node: VpnNodeRow) {
  editTarget.value = node
  Object.assign(edit, {
    name: node.name,
    city: node.city,
    max_peers: String(node.maxPeers ?? ''),
    endpoint_port: String(node.endpointPort ?? ''),
    wg_public_key: '',
  })
}

async function saveEdit() {
  const node = editTarget.value
  if (!node?.id) return
  const body: Parameters<typeof updateVpnNode>[1] = {}
  if (edit.name.trim() !== node.name) body.name = edit.name.trim()
  if (edit.city.trim() !== node.city) body.city = edit.city.trim()
  if (edit.max_peers && Number(edit.max_peers) !== node.maxPeers) body.max_peers = Number(edit.max_peers)
  if (edit.endpoint_port && Number(edit.endpoint_port) !== node.endpointPort) body.endpoint_port = Number(edit.endpoint_port)
  if (edit.wg_public_key.trim()) body.wg_public_key = edit.wg_public_key.trim()
  if (Object.keys(body).length === 0) {
    editTarget.value = null
    return
  }
  busy.value = true
  try {
    if (live) {
      await updateVpnNode(node.id, body)
      await load()
    } else {
      Object.assign(node, {
        name: body.name ?? node.name,
        city: body.city ?? node.city,
        maxPeers: body.max_peers ?? node.maxPeers,
        endpointPort: body.endpoint_port ?? node.endpointPort,
      })
    }
    toast.success('Node updated', { description: node.name })
    editTarget.value = null
  } catch (err) {
    toast.error('Node not updated', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

async function setStatus(node: VpnNodeRow, status: 'online' | 'draining') {
  if (!node.id) return
  try {
    if (live) {
      await updateVpnNode(node.id, { status })
      await load()
    } else {
      node.status = status
    }
    toast.success(status === 'online' ? 'Node is online' : 'Node is draining', {
      description: status === 'draining' ? 'No new peers; existing sessions continue.' : node.name,
    })
  } catch (err) {
    toast.error('Status not changed', { description: errorMessage(err) })
  }
}

// --- Rotate / retire -----------------------------------------------------

const rotateTarget = ref<VpnNodeRow | null>(null)
const retireTarget = ref<VpnNodeRow | null>(null)

async function confirmRotate() {
  const node = rotateTarget.value
  if (!node?.id) return
  busy.value = true
  try {
    const value = live
      ? (await rotateVpnAgentToken(node.id)).agent_token
      : 'demo-token-not-real-fedcba9876543210'
    token.value = { node: node.name, value }
    rotateTarget.value = null
  } catch (err) {
    toast.error('Token not rotated', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

async function confirmRetire() {
  const node = retireTarget.value
  if (!node?.id) return
  busy.value = true
  try {
    if (live) {
      await retireVpnNode(node.id)
      await load()
    } else {
      node.status = 'retired'
    }
    toast.success('Node retired', { description: node.name })
    retireTarget.value = null
  } catch (err) {
    // 400 while the node still has sessions: the API says to drain first.
    toast.error('Node not retired', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
      <StatTile
        v-for="(kpi, i) in vpnKpis"
        :key="kpi.label"
        :label="kpi.label"
        :value="kpi.value"
        :meta="kpi.meta"
        :icon="styleFor(i).icon"
        :accent="styleFor(i).accent"
      />
    </div>

    <div class="flex items-center justify-between gap-3">
      <h2 class="font-heading text-xl font-bold">Nodes</h2>
      <Button v-if="canWrite()" @click="createOpen = true">
        <Plus />
        Add node
      </Button>
    </div>

    <div class="grid gap-6 lg:grid-cols-3">
      <NodeCard
        v-for="node in sortedNodes"
        :key="node.id ?? node.name"
        :node="node"
        :editable="canWrite()"
        @edit="openEdit(node)"
        @set-status="(st) => setStatus(node, st)"
        @rotate="rotateTarget = node"
        @retire="retireTarget = node"
      />
    </div>

    <Card class="gap-4 py-6">
      <CardHeader class="flex flex-row flex-wrap items-start justify-between gap-3 px-6">
        <div>
          <CardTitle class="text-xl font-bold">Network traffic</CardTitle>
          <CardDescription>Combined bandwidth across all nodes</CardDescription>
        </div>
        <div class="flex gap-4 text-sm font-medium">
          <span class="inline-flex items-center gap-2"><i class="bg-chart-2 size-3 rounded-full" /> Ingress (Rx)</span>
          <span class="inline-flex items-center gap-2"><i class="bg-chart-1 size-3 rounded-full" /> Egress (Tx)</span>
        </div>
      </CardHeader>
      <CardContent class="px-6">
        <svg viewBox="0 0 900 180" class="h-52 w-full" preserveAspectRatio="none" role="img" aria-label="Ingress and egress traffic over time">
          <path
            d="M0 110 C90 90, 160 60, 240 80 S380 140, 470 95 S650 30, 740 70 S850 120, 900 95 L900 180 L0 180 Z"
            fill="color-mix(in oklch, var(--chart-2) 16%, transparent)"
          />
          <path
            d="M0 110 C90 90, 160 60, 240 80 S380 140, 470 95 S650 30, 740 70 S850 120, 900 95"
            fill="none"
            stroke="var(--chart-2)"
            stroke-width="5"
            stroke-linecap="round"
            vector-effect="non-scaling-stroke"
          />
          <path
            d="M0 140 C100 120, 170 100, 250 115 S390 150, 480 120 S660 50, 750 90 S860 130, 900 115"
            fill="none"
            stroke="var(--chart-1)"
            stroke-width="5"
            stroke-linecap="round"
            vector-effect="non-scaling-stroke"
          />
        </svg>
      </CardContent>
    </Card>
    <Dialog v-model:open="createOpen">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Add a VPN node</DialogTitle>
          <DialogDescription>
            Registers a WireGuard endpoint. It starts draining; set it online once its agent checks in.
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-4 py-2 sm:grid-cols-2">
          <div class="grid gap-2">
            <Label for="node-name">Name</Label>
            <Input id="node-name" v-model="form.name" placeholder="erbil-2" />
          </div>
          <div class="grid gap-2">
            <Label for="node-city">City</Label>
            <Input id="node-city" v-model="form.city" placeholder="Erbil" />
          </div>
          <div class="grid gap-2">
            <Label for="node-ip">Public IP</Label>
            <Input id="node-ip" v-model="form.public_ip" placeholder="203.0.113.10" />
          </div>
          <div class="grid gap-2">
            <Label for="node-port">Port</Label>
            <Input id="node-port" v-model="form.endpoint_port" type="number" min="1" max="65535" />
          </div>
          <div class="grid gap-2 sm:col-span-2">
            <Label for="node-key">WireGuard public key</Label>
            <Input id="node-key" v-model="form.wg_public_key" class="font-mono text-xs" placeholder="base64, 44 characters" />
          </div>
          <div class="grid gap-2">
            <Label for="node-cidr">Peer IP pool</Label>
            <Input id="node-cidr" v-model="form.ip_pool_cidr" placeholder="10.8.0.0/16" />
          </div>
          <div class="grid gap-2">
            <Label for="node-max">Max peers</Label>
            <Input id="node-max" v-model="form.max_peers" type="number" min="1" max="65000" />
          </div>
          <div class="grid gap-2">
            <Label for="node-service">Service</Label>
            <Input id="node-service" v-model="form.service_name" placeholder="wg-main" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">Cancel</Button>
          <Button :disabled="busy" @click="addNode">Add node</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!token" @update:open="(o) => !o && (token = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Agent token for {{ token?.node }}</DialogTitle>
          <DialogDescription>
            Copy it into the node agent's config now. It is shown only this once; only its hash is stored.
          </DialogDescription>
        </DialogHeader>
        <InkPanel class="p-4">
          <code class="font-mono text-xs break-all">{{ token?.value }}</code>
        </InkPanel>
        <DialogFooter>
          <Button variant="outline" @click="copyToken">
            <Copy />
            Copy token
          </Button>
          <Button @click="token = null">I saved it</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!editTarget" @update:open="(o) => !o && (editTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Edit {{ editTarget?.name }}</DialogTitle>
          <DialogDescription>Only the fields you change are sent.</DialogDescription>
        </DialogHeader>
        <div class="grid gap-4 py-2 sm:grid-cols-2">
          <div class="grid gap-2">
            <Label for="edit-name">Name</Label>
            <Input id="edit-name" v-model="edit.name" />
          </div>
          <div class="grid gap-2">
            <Label for="edit-city">City</Label>
            <Input id="edit-city" v-model="edit.city" />
          </div>
          <div class="grid gap-2">
            <Label for="edit-max">Max peers</Label>
            <Input id="edit-max" v-model="edit.max_peers" type="number" min="1" max="65000" />
          </div>
          <div class="grid gap-2">
            <Label for="edit-port">Port</Label>
            <Input id="edit-port" v-model="edit.endpoint_port" type="number" min="1" max="65535" />
          </div>
          <div class="grid gap-2 sm:col-span-2">
            <Label for="edit-key">New WireGuard public key</Label>
            <Input id="edit-key" v-model="edit.wg_public_key" class="font-mono text-xs" placeholder="Leave empty to keep the current key" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="editTarget = null">Cancel</Button>
          <Button :disabled="busy" @click="saveEdit">Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!rotateTarget" @update:open="(o) => !o && (rotateTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Rotate the agent token?</DialogTitle>
          <DialogDescription>
            The current token for {{ rotateTarget?.name }} stops working at once. The agent can't report until you
            install the new one.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="rotateTarget = null">Cancel</Button>
          <Button variant="destructive" :disabled="busy" @click="confirmRotate">Rotate token</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!retireTarget" @update:open="(o) => !o && (retireTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Retire {{ retireTarget?.name }}?</DialogTitle>
          <DialogDescription>
            It stops taking peers for good. A node with live sessions can't be retired: drain it and wait first.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="retireTarget = null">Cancel</Button>
          <Button variant="destructive" :disabled="busy" @click="confirmRetire">Retire node</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
