import { Navigate, Link } from 'react-router-dom'
import { getToken } from '../../shared/api/client'
import { Card } from '../../shared/ui'
import './LandingPage.css'

const PROBLEMS = [
  'Klient czeka dniami na wycenę, a w tym czasie dzwoni do konkurencji, która odpisała szybciej.',
  'Każda wycena to inne zgadywanie — ceny za podobną pracę wychodzą różne, klienci to zauważają.',
  'Wieczorami i w weekendy odpisujesz na pytania o cenę telefonem, zamiast odpoczywać.',
  'Przygotowanie profesjonalnej oferty PDF zajmuje czas, którego nie masz między zleceniami.',
  'Zapytania spoza godzin pracy giną w skrzynce albo w ogóle do Ciebie nie docierają.',
  'Nie wiesz, jak wkleić czat na swoją stronę — utykasz na etapie technicznym i odkładasz to na później.',
]

const SOLUTIONS = [
  'AI odpowiada klientowi w kilka minut, 24 godziny na dobę, 7 dni w tygodniu — nikt nie czeka do jutra.',
  'Każda wycena oparta na tym samym cenniku, który Ty ustaliłeś — zawsze konsystentna, bez zgadywania.',
  'Zapytania czekają na Ciebie w panelu — odpowiadasz i akceptujesz, kiedy Ty masz na to czas.',
  'Profesjonalna oferta PDF generuje się automatycznie — Ty tylko sprawdzasz i klikasz „Wyślij”.',
  'Żadne zapytanie nie umknie — trafia do systemu i czeka na Ciebie, nawet z nocy czy weekendu.',
  'Pomożemy Ci bezpłatnie umieścić widget czatu na Twojej stronie — wystarczy się do nas odezwać.',
]

const CUSTOM_BUILD_COSTS = [
  'Wycena projektu u software house’u: od 5 000 do kilkunastu tysięcy złotych, zanim cokolwiek zacznie działać.',
  'Realizacja trwa tygodnie, czasem miesiące — zanim dostaniesz pierwszą działającą wersję.',
  'Każda zmiana cennika czy dodanie nowej funkcji to kolejne zlecenie i kolejny rachunek.',
  'Płacisz z góry, nie wiedząc jeszcze, czy narzędzie faktycznie się sprawdzi w Twojej firmie.',
]

const AI_QUOTE_COSTS = [
  'Koszt: od 49 zł miesięcznie — żadnej jednorazowej inwestycji rzędu tysięcy złotych.',
  'Gotowe do użycia praktycznie natychmiast — zaczynasz wyceniać zlecenia jeszcze dziś.',
  'Cennik i ustawienia zmieniasz sam, w panelu, bez czekania na wykonawcę.',
  '7 dni i 3 wyceny za darmo, żeby sprawdzić, zanim zapłacisz cokolwiek.',
]

const STEPS = [
  {
    title: 'Klient pyta AI',
    description: 'Klient wchodzi na Twój link lub widget na stronie i opisuje AI, czego potrzebuje — może też wysłać zdjęcia.',
  },
  {
    title: 'AI wycenia wg Twojego cennika',
    description: 'Asystent AI zna wyłącznie sposób wyceny, który sam mu przekazałeś — nigdy nie zgaduje cen z internetu.',
  },
  {
    title: 'Ty sprawdzasz i akceptujesz',
    description: 'Widzisz roboczą wycenę AI, możesz ją poprawić, dodać lub usunąć pozycje, zanim cokolwiek trafi do klienta.',
  },
  {
    title: 'Gotowa oferta trafia do klienta',
    description: 'Jednym kliknięciem generujesz i wysyłasz profesjonalną ofertę PDF na email klienta.',
  },
]

interface PlanCardProps {
  name: string
  price: string
  quotes: string
  highlighted?: boolean
}

function PlanCard({ name, price, quotes, highlighted }: PlanCardProps) {
  return (
    <Card className={`landing-plan-card ${highlighted ? 'landing-plan-card-highlighted' : ''}`}>
      {highlighted && <div className="landing-plan-badge">Najpopularniejszy</div>}
      <h3>{name}</h3>
      <div className="landing-plan-price">
        {price} <span>/ mies.</span>
      </div>
      <p className="landing-plan-quotes">{quotes}</p>
      <Link to="/app/login" className="btn btn-primary btn-block">
        Wybierz {name}
      </Link>
    </Card>
  )
}

function LandingPage() {
  if (getToken()) {
    return <Navigate to="/app" replace />
  }

  return (
    <main className="landing-page">
      <header className="landing-nav">
        <div className="landing-brand">
          <span className="landing-brand-mark" aria-hidden="true" />
          <span className="landing-brand-name">AI Quote</span>
        </div>
        <div className="landing-nav-actions">
          <Link to="/app/login" className="btn btn-ghost">
            Zaloguj się
          </Link>
          <Link to="/app/login" className="btn btn-primary">
            Załóż darmowe konto
          </Link>
        </div>
      </header>

      <section className="landing-hero">
        <h1>Wyceniaj zlecenia klientów automatycznie, dzięki AI</h1>
        <p className="landing-hero-subtitle">
          AI Quote rozmawia z Twoimi klientami, przygotowuje wstępną wycenę na podstawie Twojego cennika i pozwala Ci
          zaakceptować lub poprawić ją, zanim trafi do klienta. Mniej czasu na wyceny, więcej na robotę.
        </p>
        <div className="landing-hero-actions">
          <Link to="/app/login" className="btn btn-primary btn-lg">
            Załóż darmowe konto
          </Link>
          <p className="landing-hero-trial-note">7 dni za darmo · 3 wyceny · bez karty płatniczej</p>
        </div>
      </section>

      <section className="landing-problem">
        <h2>Wyceny nie muszą kosztować Cię czasu i klientów</h2>
        <p className="landing-problem-subtitle">
          Niezależnie, czy jesteś hydraulikiem, elektrykiem, firmą sprzątającą czy malarzem — pierwsza rozmowa z klientem wygląda
          podobnie. Zobacz, co się zmienia, gdy AI przejmuje ją za Ciebie.
        </p>
        <div className="landing-problem-grid">
          <div className="landing-problem-column landing-problem-column-bad">
            <h3>Bez AI Quote</h3>
            <ul className="landing-problem-list">
              {PROBLEMS.map((problem) => (
                <li key={problem}>
                  <span className="landing-problem-icon landing-problem-icon-bad" aria-hidden="true">
                    ✗
                  </span>
                  <span>{problem}</span>
                </li>
              ))}
            </ul>
          </div>
          <div className="landing-problem-column landing-problem-column-good">
            <h3>Z AI Quote</h3>
            <ul className="landing-problem-list">
              {SOLUTIONS.map((solution) => (
                <li key={solution}>
                  <span className="landing-problem-icon landing-problem-icon-good" aria-hidden="true">
                    ✓
                  </span>
                  <span>{solution}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      <section className="landing-value">
        <h2>Ile kosztuje własny system wycen?</h2>
        <p className="landing-problem-subtitle">
          Sprawdziliśmy — zlecenie podobnego narzędzia software house’owi to zupełnie inna skala kosztów i czasu oczekiwania niż
          gotowe rozwiązanie.
        </p>
        <div className="landing-problem-grid">
          <div className="landing-problem-column landing-problem-column-bad">
            <h3>Wycena na zlecenie</h3>
            <ul className="landing-problem-list">
              {CUSTOM_BUILD_COSTS.map((item) => (
                <li key={item}>
                  <span className="landing-problem-icon landing-problem-icon-bad" aria-hidden="true">
                    ✗
                  </span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
          <div className="landing-problem-column landing-problem-column-good">
            <h3>AI Quote</h3>
            <ul className="landing-problem-list">
              {AI_QUOTE_COSTS.map((item) => (
                <li key={item}>
                  <span className="landing-problem-icon landing-problem-icon-good" aria-hidden="true">
                    ✓
                  </span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <p className="landing-value-callout">
          W sam raz dla małych i dużych firm — różne pakiety dopasowane do liczby zleceń, jakie obsługujesz każdego miesiąca.{' '}
          <a href="#cennik">Zobacz pakiety ↓</a>
        </p>
      </section>

      <section className="landing-steps">
        <h2>Jak to działa</h2>
        <div className="landing-steps-grid">
          {STEPS.map((step, index) => (
            <Card key={step.title} className="landing-step-card">
              <div className="landing-step-number">{index + 1}</div>
              <h3>{step.title}</h3>
              <p>{step.description}</p>
            </Card>
          ))}
        </div>
      </section>

      <section className="landing-pricing" id="cennik">
        <h2>Cennik</h2>
        <p className="landing-pricing-subtitle">Zacznij od 7-dniowego darmowego okresu próbnego — bez karty płatniczej, bez zobowiązań.</p>
        <div className="landing-pricing-grid">
          <PlanCard name="Starter" price="49 zł" quotes="15 wycen / miesiąc" />
          <PlanCard name="Growth" price="129 zł" quotes="50 wycen / miesiąc" highlighted />
          <PlanCard name="Pro" price="299 zł" quotes="150 wycen / miesiąc" />
        </div>
      </section>

      <footer className="landing-footer">
        <p>&copy; {new Date().getFullYear()} AI Quote</p>
        <Link to="/regulamin" className="landing-footer-link">
          Regulamin
        </Link>
      </footer>
    </main>
  )
}

export default LandingPage
