import { Link } from 'react-router-dom'
import { Card } from '../../shared/ui'
import './TermsPage.css'

function TermsPage() {
  return (
    <main className="terms-page">
      <header className="landing-nav">
        <div className="landing-brand">
          <span className="landing-brand-mark" aria-hidden="true" />
          <span className="landing-brand-name">AI Quote</span>
        </div>
        <Link to="/" className="btn btn-ghost">
          Powrót na stronę główną
        </Link>
      </header>

      <div className="terms-content">
        <h1>Regulamin świadczenia usługi AI Quote</h1>
        <p className="terms-updated">Ostatnia aktualizacja: {new Date().toLocaleDateString('pl-PL')}</p>

        <Card className="terms-section">
          <h2>1. Postanowienia ogólne</h2>
          <p>
            Niniejszy regulamin określa zasady korzystania z usługi AI Quote (dalej „Usługa”), dostępnej pod adresem
            https://www.wycenaonline.com.pl, świadczonej przez Mariusza Ozgę, adres e-mail kontaktowy:
            wycenaonlineai@gmail.com
            (dalej „Usługodawca”). Usługodawca jest osobą fizyczną prowadzącą działalność nierejestrowaną w
            rozumieniu art. 5 ustawy z dnia 6 marca 2018 r. – Prawo przedsiębiorców (Dz.U. z 2018 r. poz. 646 ze
            zm.) — działalność ta nie jest wpisana do CEIDG i zgodnie z przepisami nie stanowi działalności
            gospodarczej w rozumieniu tej ustawy.
          </p>
          <p>
            Usługa umożliwia przedsiębiorcom (dalej „Użytkownik” lub „Firma”) automatyczne przygotowywanie wstępnych
            wycen dla swoich klientów przy pomocy sztucznej inteligencji, na podstawie cennika i zasad wprowadzonych
            samodzielnie przez Użytkownika.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>2. Rejestracja i konto</h2>
          <p>
            Korzystanie z Usługi wymaga założenia konta poprzez podanie nazwy firmy, adresu e-mail oraz hasła, lub
            poprzez zalogowanie się za pomocą konta Google. Użytkownik zobowiązuje się podawać dane zgodne z prawdą
            oraz do zachowania poufności danych logowania.
          </p>
          <p>
            Jedno konto odpowiada jednej firmie. Usługodawca zastrzega sobie prawo do zawieszenia lub usunięcia konta
            w przypadku naruszenia niniejszego regulaminu.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>3. Okres próbny i plany płatne</h2>
          <p>
            Nowo założone konto otrzymuje 7-dniowy bezpłatny okres próbny, w ramach którego można wygenerować do 3
            wycen — w zależności od tego, co nastąpi pierwsze. Okres próbny nie wymaga podania danych karty płatniczej.
          </p>
          <p>
            Po zakończeniu okresu próbnego, aby dalej korzystać z Usługi, Użytkownik wybiera jeden z dostępnych,
            płatnych pakietów miesięcznych (Starter, Growth, Pro), różniących się limitem wycen możliwych do
            wygenerowania w danym okresie rozliczeniowym. Aktualne ceny i limity pakietów dostępne są na stronie
            głównej Usługi.
          </p>
          <p>
            Płatności obsługiwane są przez zewnętrznego operatora płatności Stripe. Usługodawca nie przechowuje
            danych kart płatniczych — są one przetwarzane wyłącznie przez Stripe zgodnie z jego regulaminem i polityką
            prywatności.
          </p>
          <p>
            Subskrypcja odnawia się automatycznie co miesiąc, do momentu jej anulowania przez Użytkownika w panelu
            zarządzania subskrypcją. Po anulowaniu Usługa pozostaje aktywna do końca opłaconego okresu rozliczeniowego.
            Usługodawca nie zwraca opłat za niewykorzystaną część okresu rozliczeniowego, chyba że bezwzględnie
            obowiązujące przepisy prawa stanowią inaczej.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>4. Zasady korzystania z Usługi</h2>
          <p>Użytkownik zobowiązuje się korzystać z Usługi zgodnie z jej przeznaczeniem oraz obowiązującym prawem, w tym w szczególności do:</p>
          <ul>
            <li>niepodejmowania prób nieautoryzowanego dostępu do systemów Usługodawcy lub kont innych Użytkowników,</li>
            <li>niewykorzystywania Usługi do generowania treści niezgodnych z prawem, wprowadzających w błąd lub naruszających prawa osób trzecich,</li>
            <li>samodzielnej weryfikacji poprawności cennika i zasad wyceny wprowadzonych do systemu.</li>
          </ul>
        </Card>

        <Card className="terms-section">
          <h2>5. Wyceny generowane przez AI</h2>
          <p>
            Wyceny przygotowywane przez asystenta AI mają charakter roboczy i pomocniczy. Ostateczna oferta trafia do
            klienta Użytkownika dopiero po jej sprawdzeniu i zatwierdzeniu przez Użytkownika w panelu. Usługodawca nie
            ponosi odpowiedzialności za treść i poprawność ofert wysłanych do klientów Użytkownika bez odpowiedniej
            weryfikacji ani za decyzje biznesowe podjęte na podstawie wycen wygenerowanych przez Usługę.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>6. Dane osobowe</h2>
          <p>
            Administratorem danych osobowych przetwarzanych w związku z korzystaniem z Usługi (w tym danych klientów
            Użytkownika przekazywanych w toku wyceny) jest Mariusz Ozga. Dane przetwarzane są w celu świadczenia
            Usługi, w tym generowania wycen, przesyłania ofert oraz obsługi płatności, zgodnie z obowiązującymi
            przepisami o ochronie danych osobowych (RODO). Kontakt w sprawach ochrony danych:
            wycenaonlineai@gmail.com.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>7. Odpowiedzialność</h2>
          <p>
            Usługodawca dokłada starań, aby Usługa działała nieprzerwanie, jednak nie gwarantuje pełnej dostępności i
            zastrzega sobie prawo do przerw technicznych. Usługodawca nie ponosi odpowiedzialności za szkody wynikłe z
            niewłaściwego korzystania z Usługi lub z przyczyn niezależnych od Usługodawcy (np. awarie po stronie
            dostawców zewnętrznych: poczty e-mail, płatności, infrastruktury chmurowej).
          </p>
        </Card>

        <Card className="terms-section">
          <h2>8. Reklamacje</h2>
          <p>
            Reklamacje dotyczące działania Usługi można zgłaszać na adres e-mail wycenaonlineai@gmail.com.
            Usługodawca rozpatruje reklamacje w terminie 14 dni od dnia ich otrzymania.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>9. Zmiany regulaminu</h2>
          <p>
            Usługodawca zastrzega sobie prawo do zmiany niniejszego regulaminu. O zmianach Użytkownicy zostaną
            poinformowani drogą elektroniczną, z odpowiednim wyprzedzeniem przed ich wejściem w życie.
          </p>
        </Card>

        <Card className="terms-section">
          <h2>10. Postanowienia końcowe</h2>
          <p>
            W sprawach nieuregulowanych niniejszym regulaminem zastosowanie mają przepisy prawa polskiego. Wszelkie
            spory będą rozstrzygane przez sąd właściwy według przepisów Kodeksu postępowania cywilnego, o ile przepisy
            bezwzględnie obowiązujące nie stanowią inaczej.
          </p>
        </Card>
      </div>

      <footer className="landing-footer">
        <p>&copy; {new Date().getFullYear()} AI Quote</p>
        <Link to="/" className="landing-footer-link">
          Strona główna
        </Link>
      </footer>
    </main>
  )
}

export default TermsPage
