import express from 'express'
import cors from 'cors'
import morgan from 'morgan'
import dotenv from 'dotenv'

dotenv.config()

const app = express()
app.use(cors())
app.use(express.json({ limit: '1mb' }))
app.use(morgan('dev'))

const PORT = process.env.PORT || 3001
const SECRET = process.env.SECRET || 'test-secret'

// In-memory deals store: { id, amount, status }
const deals = new Map()
let idSeq = 1

app.get('/', (req, res) => {
	res.json({ ok: true, deals: Array.from(deals.values()) })
})

app.post('/deal', (req, res) => {
	const amount = String(req.body?.amount || '').trim()
	if (!amount) return res.status(400).json({ error: 'amount required' })
	const id = String(idSeq++)
	deals.set(id, { id, amount, status: 'pending', createdAt: new Date().toISOString() })
	res.json({ id, amount, status: 'pending' })
})

app.post('/notify', (req, res) => {
	const auth = req.get('authorization') || ''
	if (!auth.startsWith('Bearer ') || auth.slice(7) !== SECRET) {
		return res.status(401).json({ error: 'unauthorized' })
	}
	const { amount, text, title, package: pkg, postedAt } = req.body || {}
	const amt = String(amount || '').replace(/\s+/g, '')
	// Try to match pending deal by amount
	let matched = null
	for (const deal of deals.values()) {
		if (deal.status === 'pending' && deal.amount.replace(/\s+/g, '') === amt) {
			deal.status = 'confirmed'
			deal.confirmedAt = new Date().toISOString()
			deal.match = { pkg, title, text, postedAt }
			matched = deal
			break
		}
	}
	res.json({ received: true, matched })
})

app.listen(PORT, () => {
	console.log(`Notify platform listening on http://localhost:${PORT}`)
})


