import Link from 'next/link';
import styles from './HeroLanding.module.css';

export default function HeroLanding() {
  return <main className={styles.hero}>
    <div className={styles.haze} aria-hidden="true" />
    <header className={styles.nav}>
      <Link className={styles.logo} href="/">42Hz</Link>
      <nav className={styles.links} aria-label="주 메뉴">
        <a href="#about">42Hz란</a><a href="#experience">체험 방법</a><a href="#episodes">에피소드</a>
      </nav>
      <Link className={styles.topCta} href="/experience">둘만의 공간 입장하기 <span aria-hidden="true">→</span></Link>
    </header>
    <section className={styles.content} aria-labelledby="hero-title">
      <span className={styles.kicker}>AN INTERACTIVE EXPERIENCE FOR TWO</span>
      <h1 id="hero-title" className={styles.brand}>42Hz</h1>
      <h2 className={styles.headline}>우리의 주파수는 얼마나 같을까?</h2>
      <p className={styles.subtitle}>서로의 마음을 맞춰보는 25분의 특별한 경험.</p>
      <svg className={styles.waves} viewBox="0 0 900 170" role="img" aria-label="서로 교차하는 파란색과 분홍색 주파수 파형" preserveAspectRatio="none">
        <defs><linearGradient id="blue-wave" x1="0" y1="0" x2="1" y2="0"><stop stopColor="#9cc9ff"/><stop offset="1" stopColor="#91a5ff"/></linearGradient><linearGradient id="pink-wave" x1="0" y1="0" x2="1" y2="0"><stop stopColor="#e9b5fa"/><stop offset="1" stopColor="#f4a4c7"/></linearGradient></defs>
        <path className={styles.blue} d="M0 88 C90 88 95 17 180 30 S305 151 405 90 S520 23 615 83 S755 151 900 88" stroke="url(#blue-wave)"/>
        <path className={styles.pink} d="M0 88 C90 88 115 146 205 123 S345 23 440 87 S550 149 650 83 S785 19 900 88" stroke="url(#pink-wave)"/>
      </svg>
      <Link className={styles.cta} href="/experience">둘만의 공간 입장하기 <span aria-hidden="true">→</span></Link>
    </section>
    <div className={styles.meta} id="about"><span>♡ &nbsp; 2인 참여</span><span>◷ &nbsp; 약 25분</span><span>▤ &nbsp; 우리만의 결과 카드</span></div>
  </main>;
}
