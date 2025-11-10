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
// Встроенный шаблон для М-Банка (можно переопределить через .env)
const MBANK_PAYMENT_TEMPLATE = process.env.MBANK_PAYMENT_TEMPLATE || 'mbank://transfer?amount={amount}&to={accountNumber}&comment={dealId}'

// In-memory stores
const deals = new Map()
const devices = new Map()
const accounts = new Map()
const timers = new Map()

let dealSeq = 1
let accountSeq = 1

function nowIso() {
	return new Date().toISOString()
}

function normalizeAmount(value) {
	return String(value || '').replace(/\s+/g, '')
}

function normalizeText(value) {
	return String(value || '').trim()
}

function encodeAmountForLink(amount) {
	const normalized = normalizeAmount(amount).replace(',', '.')
	return encodeURIComponent(normalized)
}

function composeAccountLabel(account) {
	const tail = account.number ? `••${String(account.number).slice(-4)}` : null
	return [account.name, account.bank, tail].filter(Boolean).join(' · ') || account.id
}

function attachAccountInfo(entity, account) {
	if (!account) return
	entity.accountId = account.id
	entity.accountLabel = account.label
	entity.accountName = account.name
	entity.accountNumber = account.number
}

function buildPaymentLink(account, amount, dealId) {
	// Используем встроенный шаблон М-Банка
	const template = MBANK_PAYMENT_TEMPLATE
	return template
		.replace(/\{amount\}/g, encodeAmountForLink(amount))
		.replace(/\{dealId\}/g, encodeURIComponent(dealId))
		.replace(/\{accountNumber\}/g, encodeURIComponent(account.number || ''))
		.replace(/\{comment\}/g, encodeURIComponent(dealId))
}

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
			deal.statusLabel = 'Отклонено (таймаут)'
			deal.statusColor = 'error'
			deals.set(dealId, deal)
		}
	}, AUTO_REJECT_MINUTES * 60 * 1000)
	timers.set(dealId, timer)
}

function cancelAutoReject(dealId) {
	if (timers.has(dealId)) {
		clearTimeout(timers.get(dealId))
		timers.delete(dealId)
	}
}

function upsertDevice({ id, label, accountId, accountLabel, source, markActivated }) {
	if (!id) return null
	const existing = devices.get(id) || {
		id,
		registeredAt: nowIso()
	}
	const updated = {
		...existing,
		label: label || existing.label || id,
		lastSeen: nowIso(),
		source: source || existing.source || 'manual'
	}

	if (accountId && accounts.has(accountId)) {
		const account = accounts.get(accountId)
		attachAccountInfo(updated, account)
	} else if (accountLabel) {
		updated.accountLabel = accountLabel
	}

	if (markActivated) {
		updated.activatedAt = existing.activatedAt || nowIso()
	}

	devices.set(id, updated)
	return updated
}

function serializeDevice(device) {
	return {
		...device,
		status: device.activatedAt ? 'active' : 'pending'
	}
}

function serializeDeal(deal) {
	return {
		...deal
	}
}

// Serve UI
app.get('/', (req, res) => {
	res.sendFile(join(__dirname, 'public', 'index.html'))
})

app.get('/api/config', (req, res) => {
	const endpoint = `${req.protocol}://${req.get('host')}/notify`
	res.json({
		ok: true,
		endpoint,
		secret: SECRET,
		autoRejectMinutes: AUTO_REJECT_MINUTES
	})
})

app.get('/api/accounts', (req, res) => {
	res.json({ ok: true, accounts: Array.from(accounts.values()) })
})

app.post('/api/account', (req, res) => {
	const name = normalizeText(req.body?.name)
	const bank = normalizeText(req.body?.bank)
	const number = normalizeText(req.body?.number)

	if (!name) {
		return res.status(400).json({ error: 'Название реквизита обязательно' })
	}

	const id = String(accountSeq++)
	const account = {
		id,
		name,
		bank,
		number,
		createdAt: nowIso()
	}
	account.label = composeAccountLabel(account)
	accounts.set(id, account)
	res.json(account)
})

app.get('/api/devices', (req, res) => {
	res.json({ ok: true, devices: Array.from(devices.values()).map(serializeDevice) })
})

app.post('/api/device', (req, res) => {
	const id = normalizeText(req.body?.deviceId)
	const label = normalizeText(req.body?.label)
	const accountId = normalizeText(req.body?.accountId)
	if (!id) {
		return res.status(400).json({ error: 'deviceId required' })
	}
	let accountLabel
	if (accountId && accounts.has(accountId)) {
		accountLabel = accounts.get(accountId).label
	}
	const device = upsertDevice({ id, label, accountId, accountLabel, source: 'manual' })
	res.json(serializeDevice(device))
})

app.post('/api/device/:id/activate', (req, res) => {
	const { id } = req.params
	if (!devices.has(id)) return res.status(404).json({ error: 'device not found' })
	const device = devices.get(id)
	device.activatedAt = device.activatedAt || nowIso()
	devices.set(id, device)
	res.json(serializeDevice(device))
})

app.post('/api/device/:id/account', (req, res) => {
	const { id } = req.params
	const accountId = normalizeText(req.body?.accountId)
	if (!devices.has(id)) return res.status(404).json({ error: 'device not found' })
	if (!accounts.has(accountId)) return res.status(400).json({ error: 'account not found' })
	const device = devices.get(id)
	const account = accounts.get(accountId)
	attachAccountInfo(device, account)
	devices.set(id, device)
	res.json(serializeDevice(device))
})

app.get('/api/deals', (req, res) => {
	res.json({ ok: true, deals: Array.from(deals.values()).map(serializeDeal) })
})

app.post('/api/deal', (req, res) => {
	const amount = normalizeText(req.body?.amount)
	const deviceId = normalizeText(req.body?.deviceId)
	const accountId = normalizeText(req.body?.accountId)

	if (!amount) {
		return res.status(400).json({ error: 'amount required' })
	}
	if (!accountId || !accounts.has(accountId)) {
		return res.status(400).json({ error: 'accountId required' })
	}

	const account = accounts.get(accountId)
	const id = String(dealSeq++)
	const deal = {
		id,
		amount,
		status: 'pending',
		statusLabel: 'Ожидание',
		statusColor: 'pending',
		createdAt: nowIso(),
		deviceId: deviceId || null,
		paymentLink: buildPaymentLink(account, amount, id)
	}
	attachAccountInfo(deal, account)

	if (deviceId) {
		const device = devices.get(deviceId)
		if (device) {
			deal.deviceLabel = device.label
		}
	}

	deals.set(id, deal)
	scheduleAutoReject(id)
	res.json(deal)
})

app.post('/api/deal/:id/close', (req, res) => {
	const { id } = req.params
	const deal = deals.get(id)
	if (!deal) return res.status(404).json({ error: 'deal not found' })
	if (deal.status !== 'pending') {
		return res.status(400).json({ error: `deal is ${deal.status}, cannot close` })
	}
	deal.status = 'confirmed'
	deal.statusLabel = 'Подтверждено вручную'
	deal.statusColor = 'success'
	deal.confirmedAt = nowIso()
	deal.confirmedBy = 'manual'
	deals.set(id, deal)
	cancelAutoReject(id)
	res.json(deal)
})

app.post('/api/deal/:id/reject', (req, res) => {
	const { id } = req.params
	const deal = deals.get(id)
	if (!deal) return res.status(404).json({ error: 'deal not found' })
	if (deal.status !== 'pending') {
		return res.status(400).json({ error: `deal is ${deal.status}, cannot reject` })
	}
	deal.status = 'rejected'
	deal.statusLabel = 'Отклонено вручную'
	deal.statusColor = 'error'
	deal.rejectedAt = nowIso()
	deal.reason = normalizeText(req.body?.reason) || 'manual'
	deals.set(id, deal)
	cancelAutoReject(id)
	res.json(deal)
})

app.post('/notify', (req, res) => {
	const auth = req.get('authorization') || ''
	if (!auth.startsWith('Bearer ') || auth.slice(7) !== SECRET) {
		return res.status(401).json({ error: 'unauthorized' })
	}

	const { amount, text, title, package: pkg, postedAt } = req.body || {}
	const amt = normalizeAmount(amount)
	const deviceId = normalizeText(req.body?.deviceId)
	const accountLabelFromDevice = normalizeText(req.body?.account)

	if (deviceId) {
		const device = upsertDevice({
			id: deviceId,
			accountLabel: accountLabelFromDevice || undefined,
			source: 'notify',
			markActivated: true
		})
		devices.set(deviceId, device)
	}

	let matched = null
	for (const deal of deals.values()) {
		if (deal.status !== 'pending') continue
		const dealAmt = normalizeAmount(deal.amount)
		const matchesAmount = amt && dealAmt === amt
		const matchesDevice = !deal.deviceId || (deviceId && deal.deviceId === deviceId)
		const dealAccountLabel = normalizeText(deal.accountLabel)
		const matchesAccount = !dealAccountLabel || (accountLabelFromDevice && normalizeText(accountLabelFromDevice) === dealAccountLabel)
		if (matchesAmount && matchesDevice && matchesAccount) {
			deal.status = 'confirmed'
			deal.statusLabel = 'Подтверждено по уведомлению'
			deal.statusColor = 'success'
			deal.confirmedAt = nowIso()
			deal.confirmedBy = 'notification'
			deal.match = { pkg, title, text, postedAt, deviceId }
			if (!deal.deviceId && deviceId) deal.deviceId = deviceId
			matched = deal
			deals.set(deal.id, deal)
			cancelAutoReject(deal.id)
			break
		}
	}

	res.json({ received: true, matched })
})

app.listen(PORT, () => {
	console.log(`Notify platform listening on http://localhost:${PORT}`)
	console.log(`Auto-reject timeout: ${AUTO_REJECT_MINUTES} minutes`)
})