import instagram from '../../assets/panel/instagram.png'

function PanelFooter() {
  return (
    <footer className="footer" id="contacto">
      <p>Sta Raquel 10815, 8310581 La Florida, Región Metropolitana</p>
      <a href="https://www.instagram.com" target="_blank" rel="noreferrer">
        <img src={instagram} alt="Instagram" />
        <span>@Amidog</span>
      </a>
    </footer>
  )
}

export default PanelFooter
