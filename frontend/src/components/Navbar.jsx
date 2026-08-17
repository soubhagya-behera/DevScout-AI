function Navbar() {
  return (
    <header className="navbar">
      <div className="navbar-inner">
        <a
          href="#product"
          className="brand"
          aria-label="DevScout home"
        >
          <span className="brand-mark" aria-hidden="true"></span>
          <span className="brand-name">DevScout</span>
        </a>

        <nav className="nav-links" aria-label="Primary">
          <a href="#product">Product</a>
          <a href="#how-it-works">How it works</a>
          <a
            href="https://github.com"
            target="_blank"
            rel="noreferrer"
          >
            GitHub
          </a>
        </nav>

        <div className="nav-actions">
          <a href="#product" className="btn btn-primary btn-sm">
            Get started
          </a>
        </div>
      </div>
    </header>
  );
}

export default Navbar;