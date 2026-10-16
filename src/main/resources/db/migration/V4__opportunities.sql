-- Stored savings and investment ideas for internal staff. Each row must cite a public source.

CREATE TABLE opportunity (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  summary VARCHAR(2000) NOT NULL,
  audience VARCHAR(20) NOT NULL,
  source_name VARCHAR(200) NOT NULL,
  source_url VARCHAR(500) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO opportunity (title, summary, audience, source_name, source_url) VALUES
('Move part of a salary onto a savings account',
 'A current account is for payments. A savings passbook is for money you do not need this month: the bank pays interest on its own schedule, and outgoing transfers are often blocked so the balance stays put. Open the source for who can open one, the minimum balance, and how interest is paid. Do not assume a rate that is not written there.',
 'PERSONAL',
 'Afriland First Bank — Comptes particuliers',
 'https://www.afrilandfirstbank.com/individuals/'),
('Read the fee schedule before leaving a salary on a current account',
 'Account opening, the minimum balance, and when interest is credited are in the bank conditions, not in a slogan. The same page explains that a savings account is built to receive money and not to pay out from it. Use it before deciding that one salary account is enough.',
 'PERSONAL',
 'Afriland First Bank — FAQ',
 'https://www.afrilandfirstbank.com/faq/'),
('Subscribe to Treasury bills through a partner bank',
 'Cameroon issues short-term Treasury bills (BTA) and longer bonds (OTA) on the BEAC public-securities market. A person does not buy them at BEAC: a bank that is a Treasury specialist (SVT) subscribes for them. Several banks that work with firms like IWOMI are on that list. Tenors and results change at every auction, so the source is the description of the market, not a promised yield.',
 'PERSONAL',
 'BEAC — Présentation du marché des titres publics',
 'https://www.beac.int/m-des-titres-publics/presentation-generale/'),
('Place company cash that payroll does not need',
 'The same public-securities market is open to companies, again only through an SVT bank. It fits surplus cash with a known date, not money that must pay salaries next week. Confirm the next auction, the minimum, and eligibility on the source before moving company funds.',
 'COMPANY',
 'BEAC — Présentation du marché des titres publics',
 'https://www.beac.int/m-des-titres-publics/presentation-generale/'),
('Check what is actually offered this month',
 'Each Treasury issue has its own tenor and result. Open the latest Cameroon announcement and the result communiqué. An old rate is not an offer.',
 'BOTH',
 'BEAC — Annonces et communiqués des titres publics',
 'https://www.beac.int/m-des-titres-publics/annonces-et-communiques/'),
('Compare recent yields instead of guessing',
 'BEAC publishes the yield curve of Cameroon public securities. Use the latest Cameroon file to see what recent issues returned, then confirm a live subscription with the bank. The curve is history, not a quote.',
 'BOTH',
 'BEAC — Statistiques des titres publics',
 'https://www.beac.int/economie-stats/statistiques-titres-publics/');
