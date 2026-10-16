# IWOMI Technologies (public knowledge, safe to share with visitors)

IWOMI Technologies is a Cameroonian fintech company founded in 2015 by Mr. Collins Fomba and headquartered in Douala.
Tagline: "Infrastructure for Financial Inclusion". It brings innovative, reliable and secure solutions that make banking and financial
services more accessible, affordable and available to everyone, while meeting regulatory requirements.
Aim: help banks and other financial institutions manage and carry out financial activities safely and conveniently.
IWOMI offers digital financial solutions and software for business to business (B2B) and business to consumers (B2C).

Known products (the live catalog in the context is the source of truth for status and components):
- BKWIKI: knowledge and regulatory information about banks in Africa.
- IWOMI PAY: a payment gateway that lets customers pay for a package or purchase online without contacting the seller; it facilitates online payment of goods and services.
- LOAN 360: lets banks and other financial institutions collect and track loans.
- IWAKAP: a web platform for fundraising campaigns, enabling solidarity and support from donors worldwide.

# General fintech guidance (Cameroon / CEMAC context; general information, not legal or financial advice)

- Currency: the CFA franc (XAF, FCFA). Central bank: BEAC. Banking and microfinance supervisor in Central Africa: COBAC.
- Mobile money operators in Cameroon include Orange Money and MTN Mobile Money (MoMo).
- Typical KYC for individuals: national ID card (CNI) or passport, a recent photo, proof of address, phone number. For businesses:
  trade register extract (RCCM), taxpayer number (NIU), statutes, ID of the legal representative.
- Failed or pending mobile money / bank transfer: do not retry immediately (risk of a double debit); wait for the confirmation or failure message;
  check the balance and transaction history; keep the transaction ID, date and time, amount, and the numbers involved; then contact the operator or the bank,
  and the app's support if it is an IWOMI solution. Reversals of failed transactions usually happen automatically within a short delay.
- Security: never share your PIN, password, one-time code (OTP) or card number with anyone, including people claiming to be from a bank or from IWOMI.
- Loans: a lender usually checks identity, repayment capacity (income and expenses), existing debts and guarantees; a repayment schedule lists each installment (principal + interest).
- Agency banking: a bank agent (shop or kiosk) offers deposits, withdrawals and transfers on behalf of a bank, with transactions recorded in the bank's core system.
- Integration vocabulary: core banking system, SOAP and REST APIs, webhooks / callbacks, idempotency keys, reconciliation, settlement, audit trail.
