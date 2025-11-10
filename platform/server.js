import express from 'express'
import cors from 'cors'
import morgan from 'morgan'
import dotenv from 'dotenv'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

dotenv.config()

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const app = express()
app.use(cors())
app.use(express.json({ limit: '1mb' }))
app.use(express.static(join(__dirname, 'public')))
app.use(morgan('dev'))

const PORT = process.env.PORT || 3001
const SECRET = process.env.SECRET || 'test-secret'
const AUTO_REJECT_MINUTES = parseInt(process.env.AUTO_REJECT_MINUTES || '15', 10)

// In-memory stores
const deals = new Map()
const timers = new Map()
const devices = new Map()
let idSeq = 1

function nowIso() {
	return new Date().toISOString()
}

function normalizeAmount(v) {
	return String(v || '').replace(/\s+/g, '')
}

function normalizeAccount(v) {
	return String(v || '').trim()
}

function upsertDevice({ id, account, source }) {
	if (!id) return null
	const existing = devices.get(id) || {
		id,
		registeredAt: nowIso()
	}
	const updated = {
		...existing,
		account: account || existing.account || null,
		lastSeen: nowIso(),
		source: source || existing.source || 'unknown'
	}
	devices.set(id, updated)
	return updated
}

// Auto-reject deals after 15 minutes
function scheduleAutoReject(dealId) {
	if (timers.has(dealId)) {
		clearTimeout(timers.get(dealId))
	}
	const timer = setTimeout(() => {
		const deal = deals.get(dealId)
		if (deal && deal.status === 'pending') {
			deal.status = 'rejected'
			deal.rejectedAt = nowIso()
			deal.reason = 'timeout'
			timers.delete(dealId)
			console.log(`Deal ${dealId} auto-rejected after ${AUTO_REJECT_MINUTES} minutes`)
		}
	}, AUTO_REJECT_MINUTES * 60 * 1000)
	timers.set(dealId, timer)
}

// Cancel auto-reject timer
function cancelAutoReject(dealId) {
	if (timers.has(dealId)) {
		clearTimeout(timers.get(dealId))
		timers.delete(dealId)
	}
}

// Serve web interface
app.get('/', (req, res) => {
	res.sendFile(join(__dirname, 'public', 'index.html'))
})

// API: Get all deals
app.get('/api/deals', (req, res) => {
	res.json({ ok: true, deals: Array.from(deals.values()) })
})

// API: Get devices
app.get('/api/devices', (req, res) => {
	res.json({ ok: true, devices: Array.from(devices.values()) })
})

// API: Create / update device manually
app.post('/api/device', (req, res) => {
	const deviceId = String(req.body?.deviceId || '').trim()
	if (!deviceId) {
		return res.status(400).json({ error: 'deviceId required' })
}
	const account = normalizeAccount(req.body?.account || '')
	const device = upsertDevice({ id: deviceId, account, source: 'manual' })
	res.json(device)
})

// API: Create new deal
app.post('/api/deal', (req, res) => {
	const amount = String(req.body?.amount || '').trim()
	if (!amount) return res.status(400).json({ error: 'amount required' })
	const deviceId = String(req.body?.deviceId || '').trim()
	let account = normalizeAccount(req.body?.account || '')
	if (!account && deviceId && devices.has(deviceId)) {
		account = devices.get(deviceId).account || ''
	}
	const id = String(idSeq++)
	const deal = {
		id,
		amount,
		status: 'pending',
		deviceId: deviceId || null,
		account: account || null,
		createdAt: nowIso()
	}
	deals.set(id, deal)
	scheduleAutoReject(id)
	res.json(deal)
})

// API: Close deal manually
app.post('/api/deal/:id/close', (req, res) => {
	const { id } = req.params
	const deal = deals.get(id)
	if (!deal) return res.status(404).json({ error: 'deal not found' })
	if (deal.status !== 'pending') {
		return res.status(400).json({ error: `deal is ${deal.status}, cannot close` })
	}
	deal.status = 'confirmed'
	deal.confirmedAt = nowIso()
	deal.confirmedBy = 'manual'
	cancelAutoReject(id)
	res.json(deal)
})

// API: Reject deal manually
app.post('/api/deal/:id/reject', (req, res) => {
	const { id } = req.params
	const deal = deals.get(id)
	if (!deal) return res.status(404).json({ error: 'deal not found' })
	if (deal.status !== 'pending') {
		return res.status(400).json({ error: `deal is ${deal.status}, cannot reject` })
	}
	deal.status = 'rejected'
	deal.rejectedAt = nowIso()
	deal.reason = req.body?.reason || 'manual'
	cancelAutoReject(id)
	res.json(deal)
})

// API: Receive notification from Android app
app.post('/notify', (req, res) => {
	const auth = req.get('authorization') || ''
	if (!auth.startsWith('Bearer ') || auth.slice(7) !== SECRET) {
		return res.status(401).json({ error: 'unauthorized' })
	}
	const { amount, text, title, package: pkg, postedAt } = req.body || {}
	const amt = normalizeAmount(amount)
	const deviceId = String(req.body?.deviceId || '').trim()
	const account = normalizeAccount(req.body?.account || '')
	if (deviceId) {
		upsertDevice({ id: deviceId, account: account || null, source: 'notify' })
	}
	// Try to match pending deal by amount
	let matched = null
	for (const deal of deals.values()) {
		if (deal.status === 'pending') {
			const dealAmt = normalizeAmount(deal.amount)
			const matchesDevice = !deal.deviceId || (deviceId && deal.deviceId === deviceId)
			const matchesAccount = !deal.account || (account && normalizeAccount(deal.account) === account)
			if (dealAmt === amt && matchesDevice && matchesAccount) {
				deal.status = 'confirmed'
				deal.confirmedAt = nowIso()
				if (!deal.deviceId && deviceId) deal.deviceId = deviceId
				if (!deal.account && account) deal.account = account
				deal.match = { pkg, title, text, postedAt, deviceId }
				matched = deal
				cancelAutoReject(deal.id)
				break
			}
		}
	}
	res.json({ received: true, matched })
})

app.listen(PORT, () => {
	console.log(`Notify platform listening on http://localhost:${PORT}`)
	console.log(`Auto-reject timeout: ${AUTO_REJECT_MINUTES} minutes`)
})


