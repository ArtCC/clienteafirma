/*
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo  bajo licencia GPL version 2  segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */

package es.gob.afirma.applet;

import java.awt.Component;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import es.gob.afirma.core.AOCancelledOperationException;
import es.gob.afirma.core.AOException;
import es.gob.afirma.core.AOInvalidFormatException;
import es.gob.afirma.core.envelopers.AOEnveloper;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.ui.AOUIFactory;
import es.gob.afirma.envelopers.cms.AOCMSEnveloper;
import es.gob.afirma.envelopers.cms.AOInvalidRecipientException;
import es.gob.afirma.keystores.main.common.AOCertificatesNotFoundException;
import es.gob.afirma.keystores.main.common.AOKeyStoreManagerException;

/** Manejador para el ensobrado de datos por parte del Applet Cliente @firma.
 * Esta clase almacena toda la informaci&oacute;n relevante para las operaciones
 * de ensobrado y desensobrado y proporciona las funcionalidades b&aacute;sicas
 * para realizar estas operaciones. */
public final class EnveloperManager {
    
    private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

    /** Componente padre sobre el que mostrar los di&aacute;logos modales. */
    private Component parent = null;

    /** Tipo de contenido de la estructura CMS que se desea generar. */
    private String contentType = null;

    /** Manejador de almacenes del que extraer las claves para el ensobrado y
     * desensobrado de datos cuando no se indique la clave del remitente. */
    private KeyStoreConfigurationManager ksConfigManager = null;

    /** Listado de certificados de destinatarios para el sobre digital. */
    private Map<BigInteger, X509Certificate> recipients = null;

    /** Algoritmo de firma para la generaci&oacute;n del sobre. */
    private String signAlgorithm = AOSignConstants.DEFAULT_SIGN_ALGO;

    /** Manejador de cifrado para tratar el cifrado interno de los sobres. */
    private CipherManager cipherManager = null;

    /** Envoltorio CMS. */
    private byte[] envelopedData = null;

    /** Datos del envoltorio. */
    private byte[] contentData = null;

    /** Ruta a un fichero de entrada de datos. */
    private URI fileUri = null;

    /** Indica si el contenido del fichero introducido est&aacute; codificado en
     * Base 64. */
    private boolean fileBase64 = false;

    /** Objeto para la envoltura de datos. */
    private AOCMSEnveloper enveloper;

    /** Construye el objeto. */
    public EnveloperManager() {
        this.enveloper = new AOCMSEnveloper();
        this.cipherManager = new CipherManager();
    }

    /** Construye el objeto y establece un componente padre sobre el que mostrar
     * los di&aacute;logos modales.
     * @param parent
     *        Componente sobre el que mostrar los di&aacute;logos. */
    public EnveloperManager(final Component parent) {
        this();
        this.parent = parent;
    }

    /** Inicializa la configuraci&oacute;n del manejador a la
     * configuraci&oacute;n por defecto. */
    public void initialize() {
        this.signAlgorithm = AOSignConstants.DEFAULT_SIGN_ALGO;
        this.cipherManager.initialize();
        this.contentType = null;
        this.recipients = null;
        this.fileUri = null;
        this.fileBase64 = false;
        this.enveloper = new AOCMSEnveloper();
        this.envelopedData = null;
        this.contentData = null;
    }

    /** Recupera la URI del fichero configurado. Si no hay ninguno, se devuelve {@code null}.
     * @return URI del fichero.
     * @see #setFileUri(URI, boolean) */
    public URI getFileUri() {
        return this.fileUri;
    }

    /** Indica si el contenido del fichero configurado a trav&eacute;s de su URI
     * es base 64.
     * @return Devuelve {@code true} si el contenido del fichero es Base 64.
     * @see #setFileUri(URI, boolean) */
    public boolean isFileBase64() {
        return this.fileBase64;
    }

    /** Establece el fichero a utilizar.
     * @param fileUri
     *        Ruta del fichero.
     * @param fileBase64
     *        Indica si el contenido del fichero es base 64. */
    public void setFileUri(final URI fileUri, final boolean fileBase64) {
        this.fileUri = fileUri;
        this.fileBase64 = fileBase64;
    }

    /** Recupera el tipo de envoltorio de datos configurado.
     * @return Tipo de envoltorio. */
    public String getCmsContentType() {
        return this.contentType;
    }

    /** Establece el manejador de cifrado.
     * @param manager
     *        Manejador de cifrado. */
    public void setCipherManager(final CipherManager manager) {
        this.cipherManager = manager;
    }

    /** Establece el tipo de envoltorio que se debe generar. Los tipos soportados
     * est&aacute;n declarados en {@link AOConstants}.
     * @param cmsContentType
     *        Tipo de envoltorio. */
    public void setCmsContentType(final String cmsContentType) {
        this.contentType = cmsContentType;
    }

    /** Recupera los certificados de los destinatarios de un sobre
     * electr&oacute;nico.
     * @return Listado de certificados. */
    public X509Certificate[] getRecipients() {
        return this.recipients.values().toArray(new X509Certificate[0]);
    }

    /** Establece el algoritmo para las firmas de los sobres. Si no se establece
     * uno, se utilizar&aacute; el por defecto.
     * @param signAlgorithm
     *        Algoritmo de firma. */
    public void setSignAlgorithm(final String signAlgorithm) {
        this.signAlgorithm = (signAlgorithm == null ? AOSignConstants.DEFAULT_SIGN_ALGO : signAlgorithm);
    }

    /** Recupera el algoritmo de firma configurado.
     * @return Algoritmo de firma. */
    public String getSignAlgorithm() {
        return this.signAlgorithm;
    }

    /** Establece el manejador de almacenes a utilizar.
     * @param manager
     *        Manejador de almacenes. */
    public void setKsConfigManager(final KeyStoreConfigurationManager manager) {
        this.ksConfigManager = manager;
    }

    /** Agrega un nuevo destinatario al sobre electr&oacute;nico.
     * @param cert
     *        Certificado del nuevo destinatario. */
    public void addRecipient(final X509Certificate cert) {
        if (cert == null) {
            return;
        }

        if (this.recipients == null) {
            this.recipients = new HashMap<BigInteger, X509Certificate>();
        }

        if (!this.recipients.containsKey(cert.getSerialNumber())) {
            this.recipients.put(cert.getSerialNumber(), cert);
        }
    }

    /** Agrega un nuevo destinatario al sobre electr&oacute;nico.
     * @param certEncoded
     *        Certificado codificado del nuevo destinatario.
     * @throws CertificateException */
    public void addRecipient(final byte[] certEncoded) throws CertificateException {
        addRecipient(encodeCertificate(certEncoded));
    }

    /** Elimina un destinatario del sobre electr&oacute;nico.
     * @param cert */
    public void removeRecipient(final X509Certificate cert) {
        if (cert == null || this.recipients == null) {
            return;
        }

        this.recipients.remove(cert.getSerialNumber());
        if (this.recipients.isEmpty()) {
            this.recipients = null;
        }
    }

    /** Agrega un nuevo destinatario al sobre electr&oacute;nico.
     * @param certEncoded
     *        Certificado codificado del nuevo destinatario.
     * @throws CertificateException */
    public void removeRecipient(final byte[] certEncoded) throws CertificateException {
        removeRecipient(encodeCertificate(certEncoded));
    }

    /** Elimina todos los destinatarios configurados para el sobre
     * electr&oacute;nico. */
    public void removeAllRecipients() {
        this.recipients = null;
    }

    /** Obtiene un certificado a partir de su codificaci&oacute;n.
     * @param cert
     *        Certificado codificado.
     * @return Certificado decodificado.
     * @throws AOCertificateException
     *         Cuando ocurre un error en la decodificaci&oacute;n del
     *         certificado. */
    private X509Certificate encodeCertificate(final byte[] cert) throws CertificateException {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509") //$NON-NLS-1$
                                                       .generateCertificate(new ByteArrayInputStream(cert));
        }
        catch (final Exception e) {
            throw new CertificateException("No se ha podido decodificar el certificado proporcionado", e); //$NON-NLS-1$
        }
    }

    /** Genera un sobre electr&oacute;nico.
     * @throws IOException
     *         Cuando ocurre un error en la lectura de los datos.
     * @throws NoSuchAlgorithmException
     *         Cuando el algoritmo de firma no est&aacuta; soportado.
     * @throws CertificateEncodingException
     *         Cuando el certificado del remitente no es v&aacute;lido.
     * @throws AOException
     *         Cuando ocurre algun error al envolver los datos.
     * @throws IllegalArgumentException
     *         Cuando no se ha indicado un par&aacute;metro o se
     *         configur&oacute; uno err&oacute;neo.
     * @throws AOCancelledOperationException
     *         Cuando el usuario cancela la operaci&oacute;n. */
    public void envelop() throws IOException, NoSuchAlgorithmException, AOException, CertificateEncodingException {
        this.envelop(getConfigureContent());
    }

    /** Genera un sobre electr&oacute;nico. No guarda en el manager los datos que
     * se le pasan por par&aacute;metro.
     * @param content
     *        Contenido que se desea ensobrar.
     * @throws IOException
     *         Cuando ocurre un error en la lectura de los datos.
     * @throws NoSuchAlgorithmException
     *         Cuando el algoritmo de firma no est&aacuta; soportado.
     * @throws CertificateEncodingException
     *         Cuando el certificado del remitente no es v&aacute;lido.
     * @throws AOException
     *         Cuando ocurre algun error al envolver los datos.
     * @throws NullPointerException
     *         Cuando se ha indicado el tipo de contenido o los
     *         destinatarios del mismo.
     * @throws IllegalArgumentException
     *         Cuando se configur&oacute; un par&aacute;metro
     *         err&oacute;neo.
     * @throws AOCancelledOperationException
     *         Cuando el usuario cancela la operaci&oacute;n. */
    public void envelop(final byte[] content) throws CertificateEncodingException,
                                             NoSuchAlgorithmException,
                                             IOException,
                                             AOException,
                                             AOCancelledOperationException {

        if (this.contentType == null) {
            LOGGER.severe("No se ha indicado el tipo de sobre electronico"); //$NON-NLS-1$
            throw new IllegalArgumentException("No se ha indicado el tipo de sobre electronico"); //$NON-NLS-1$
        }

        if ((this.recipients == null || this.recipients.isEmpty())) {
            LOGGER.severe("No se han indicado los destinatarios del sobre electronico"); //$NON-NLS-1$
            throw new IllegalArgumentException("No se han indicado los destinatarios del sobre electronico"); //$NON-NLS-1$
        }

        if (this.contentType.equals(AOSignConstants.CMS_CONTENTTYPE_ENVELOPEDDATA)) {
            this.envelopedData = createCMSEnvelopData(content);
        }
        else if (this.contentType.equals(AOSignConstants.CMS_CONTENTTYPE_SIGNEDANDENVELOPEDDATA)) {
            this.envelopedData = createCMSSignedEnvelopData(content);
        }
        else if (this.contentType.equals(AOSignConstants.CMS_CONTENTTYPE_AUTHENVELOPEDDATA)) {
            this.envelopedData = createCMSAuthenticatedEnvelopData(content);
        }
        else {
            throw new IllegalArgumentException("Tipo de envoltorio CMS no soportado"); //$NON-NLS-1$
        }
    }

    /** Crea una estructura EncryptedData de CMS usando una clave de cifrado.
     * @throws IOException
     *         Cuando ocurre un error en la lectura de los datos.
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso
     * @throws NoSuchAlgorithmException
     *         Cuando el algoritmo de cifrado no est&aacute; soportado. * @throws
     *         IOException */
    public void encrypt() throws IOException, NoSuchAlgorithmException, AOException {
        this.encrypt(getConfigureContent());
    }

    /** Cifra un contenido usando para una clave de cifrado.
     * @param content
     *        Datos a encriptar
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso
     * @throws NoSuchAlgorithmException
     *         Cuando el algoritmo de cifrado no est&aacute; soportado. */
    public void encrypt(final byte[] content) throws AOException, NoSuchAlgorithmException {
        this.envelopedData = createCMSEncryptedData(content);
    }

    /** Desenvuelve el sobre electr&oacute;nico configurado.
     * @throws AOInvalidRecipientException
     *         Cuando el usuario no es uno de los destinatarios del sobre.
     * @throws CertificateEncodingException
     *         Cuando el certificado del destinatario no es v&aacute;lido.
     * @throws IOException
     *         Cuando el envoltorio est&aacute; corrupto o no puede leerse.
     * @throws AOInvalidFormatException
     *         Cuando no se ha indicado un envoltorio soportado.
     * @throws AOException
     *         Cuando se produce un error durante al desenvolver los datos. */
    public void unwrap() throws CertificateEncodingException, AOInvalidRecipientException, AOInvalidFormatException, IOException, AOException {
        this.unwrap(getConfigureEnvelop());
    }

    /** Desenvuelve el sobre electr&oacute;nico proporcionado.
     * @param envelop
     *        Sobre electr&oacute;nico a desenvolver
     * @throws AOInvalidRecipientException
     *         Cuando el usuario no es uno de los destinatarios del sobre.
     * @throws CertificateEncodingException
     *         Cuando el certificado del destinatario no es v&aacute;lido.
     * @throws IOException
     *         Cuando el envoltorio est&aacute; corrupto o no puede leerse.
     * @throws AOInvalidFormatException
     *         Cuando no se ha indicado un envoltorio soportado.
     * @throws AOException
     *         Cuando se produce un error durante al desenvolver los datos. */
    public void unwrap(final byte[] envelop) throws CertificateEncodingException,
                                            AOInvalidRecipientException,
                                            AOInvalidFormatException,
                                            IOException,
                                            AOException {

        // Comprobamos si requiere un certificado para la extraccion de los
        // datos
        if (this.enveloper.isCMSValid(envelop, AOSignConstants.CMS_CONTENTTYPE_ENVELOPEDDATA) || this.enveloper.isCMSValid(envelop,
                                                                                                             AOSignConstants.CMS_CONTENTTYPE_SIGNEDANDENVELOPEDDATA)
            || this.enveloper.isCMSValid(envelop, AOSignConstants.CMS_CONTENTTYPE_AUTHENVELOPEDDATA)) {
            if (!this.ksConfigManager.isSelectedCertificate()) {
                try {
                    this.ksConfigManager.selectCertificate();
                }
                catch (final Exception e) {
                    throw new AOException("Error al obtener el certificado seleccionado", e);
                }
            }
            this.enveloper.setOriginatorKe(this.ksConfigManager.getCertificateKeyEntry());
        }
        else if (this.enveloper.isCMSValid(envelop, AOSignConstants.CMS_CONTENTTYPE_ENCRYPTEDDATA)) {
            if (this.cipherManager.getCipherAlgorithm().supportsKey()) {
                this.enveloper.setCipherKey(this.cipherManager.getCipherB64Key());
            }
            else {
                this.enveloper.setCipherKey(String.valueOf(this.cipherManager.getCipherPassword()));
            }
        }

        try {
            this.contentData = this.enveloper.recoverData(envelop);
        }
        catch (final InvalidKeyException e) {
            throw new AOException("La clave interna del sobre no es valida", e); //$NON-NLS-1$
        }
    }

    /** Recupera los datos configurados como entrada para la operacion de
     * ensobrado. Si no hay datos configurados muestra un di&aacute;logo para la
     * selecci&oacute;n de un fichero.
     * @return Contenido configurados.
     * @throws IOException
     *         Cuando ocurre algun problema en la lectura de los datos. */
    private byte[] getConfigureContent() throws IOException {
        if (this.contentData == null) {
            return recoverDataFromFile();
        }
        return this.contentData.clone();
    }

    /** Obtiene los datos configurados como entrada para la operacion. Si no hay
     * un envoltorio configurado muestra un di&aacute;logo para la
     * selecci&oacute;n de un fichero.
     * @return Envoltorio configurado.
     * @throws IOException
     *         Cuando ocurre algun problema en la lectura de los datos. */
    private byte[] getConfigureEnvelop() throws IOException {
        if (this.envelopedData == null) {
            return recoverDataFromFile();
        }
        return this.envelopedData.clone();
    }

    /** Recupera el contenido del fichero. Si no hay ning&uacute;n fichero
     * configurado, muestra un di&aacute;logo al usuario para que lo seleccione.
     * @return Contenido del fichero.
     * @throws IOException
     *         Cuando ocurre un error al determinar o leer el fichero
     *         configurado. */
    private byte[] recoverDataFromFile() throws IOException {
        // Fichero de entrada
        if (this.fileUri == null) {
            try {
                this.fileUri = AOUtil.createURI(AOUIFactory.getLoadFileName(null, null, this.parent));
            }
            catch (final Exception e) {
                throw new IOException("Se ha proporcionado un nombre de fichero no valido '" + this.fileUri + "': " + e); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        // En este punto, tenemos la URI de los datos de entrada
        InputStream is = null;
        try {
            is = AOUtil.loadFile(this.fileUri, this.parent, true, this.fileBase64);
            return AOUtil.getDataFromInputStream(is);
        }
        catch (final AOException e) {
            throw new IOException("No se pudieron leer los datos del fichero seleccionado: " + e); //$NON-NLS-1$
        }
        finally {
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (final Exception e) {
                // Ignoramos los errores en el cierre
            }
        }
    }

    /** Genera un envoltorio CMS (no sobre electr&oacute;nico) de tipo
     * EncryptedData.
     * @param content
     *        Datos que queremos ensobrar.
     * @return Envoltorio CMS ed tipo EncryptedData.
     * @throws AOException
     *         Cuando ocurre un error durante la operaci&oacute;n.
     * @throws NoSuchAlgorithmException
     *         Cuando la configuraci&oacute;n de cifrado no sea
     *         v&aacute;lida. */
    private byte[] createCMSEncryptedData(final byte[] content) throws AOException, NoSuchAlgorithmException {
        return this.enveloper.createCMSEncryptedData(content, this.cipherManager.getCipherConfig(), this.cipherManager.getConfiguredKey());
    }

    /** Genera un sobre de tipo EnvelopedData.
     * @param content
     *        Datos que queremos ensobrar.
     * @param originatorKe
     *        Clave del remitente del sobre.
     * @return Sobre electr&oacute;nico.
     * @throws IOException
     *         Cuando ocurre un error de lectura de datos.
     * @throws CertificateEncodingException
     *         El certificado de firma no es v&aacute;lido.
     * @throws NoSuchAlgorithmException
     *         Algoritmo no soportado. 
     * @throws AOException */
    private byte[] createCMSEnvelopData(final byte[] content) throws IOException, CertificateEncodingException, NoSuchAlgorithmException, AOException {
        return this.enveloper.createCMSEnvelopedData(content, this.ksConfigManager.getCertificateKeyEntry(), // Si hay uno
                                                                                                   // seleccionado, se
                                                                                                   // utiliza
                                                this.cipherManager.getCipherConfig(),
                                                this.getRecipients());
    }

    /** Genera un sobre de tipo SignedAndEnvelopedData.
     * @param content
     *        Datos que queremos ensobrar.
     * @param originatorKe
     *        Clave del remitente del sobre.
     * @return Sobre electr&oacute;nico.
     * @throws AOException
     *         Cuando ocurre un error durante la operaci&oacute;n.
     * @throws IOException
     *         Cuanto hay algun problema en la lectura de datos.
     * @throws NoSuchAlgorithmException
     * @throws CertificateEncodingException
     * @throws IllegalArgumentException
     *         Cuando no se ha introducido alg&uacute;n par&aacute;metro
     *         requerido por el tipo de sobre */
    private byte[] createCMSSignedEnvelopData(final byte[] content) throws AOException,
                                                                   CertificateEncodingException,
                                                                   NoSuchAlgorithmException,
                                                                   IOException {
        if (!this.ksConfigManager.isSelectedCertificate()) {
            try {
                this.ksConfigManager.selectCertificate();
            }
            catch (final Exception e) {
                throw new AOException("Error al seleccionar el certificado", e); //$NON-NLS-1$
            }
        }
        return this.enveloper.createCMSSignedAndEnvelopedData(content,
                                                         this.ksConfigManager.getCertificateKeyEntry(),
                                                         this.cipherManager.getCipherConfig(),
                                                         this.getRecipients());
    }

    /** Genera un sobre de tipo AuthenticatedEnvelopedData.
     * @param content
     *        Datos que queremos ensobrar.
     * @return Sobre electr&oacute;nico.
     * @throws AOException
     *         Cuando ocurre un error durante la operaci&oacute;n.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateEncodingException
     * @throws IllegalArgumentException
     *         Cuando no se ha introducido alg&uacute;n par&aacute;metro
     *         requerido por el tipo de sobre */
    private byte[] createCMSAuthenticatedEnvelopData(final byte[] content) throws AOException,
                                                                          CertificateEncodingException,
                                                                          NoSuchAlgorithmException,
                                                                          IOException {
        if (!this.ksConfigManager.isSelectedCertificate()) {
            try {
                this.ksConfigManager.selectCertificate();
            }
            catch (final Exception e) {
                throw new AOException("Error al seleccionar el certificado", e); //$NON-NLS-1$
            }
        }
        return this.enveloper.createCMSAuthenticatedEnvelopedData(content,
                                                             this.ksConfigManager.getCertificateKeyEntry(),
                                                             this.cipherManager.getCipherConfig(),
                                                             this.getRecipients());
    }

    /** Agrega un nuevo remitente a un sobre electr&oacute;nico. Los tipos de
     * contenido CMS que soportan m&uacute;ltiples remitentes son EnvelopedData,
     * SignedAndEnvelopedData y AuthenticatedAndEnvelopedData.
     * @param envelop
     *        Envoltorio al que se desea agrehar el nuevo remitente.
     * @return Sobre electr&oacute;nico con el remitente agregado.
     * @throws AOException
     *         Cuando se produce un error durante el proceso de ensobrado.
     * @throws AOCancelledOperationException
     *         Cuando el usuario cancela la operaci&oacute;n.
     * @throws AOCertificatesNotFoundException
     *         Cuando no hay certificados en el almac&eacute;n seleccionado.
     * @throws AOKeyStoreManagerException
     *         Cuando no se puede acceder al almac&eacute;n de certificados.
     * @throws AOInvalidFormatException
     *         Tipo de envoltorio no soportado. */
    public byte[] coEnvelop(final byte[] envelop) throws AOException,
                                                 AOCancelledOperationException,
                                                 AOCertificatesNotFoundException,
                                                 AOKeyStoreManagerException,
                                                 AOInvalidFormatException {
        if (!this.ksConfigManager.isSelectedCertificate()) {
            try {
                this.ksConfigManager.selectCertificate();
            }
            catch (final Exception e) {
                throw new AOException("Error al seleccionar el certificado", e); //$NON-NLS-1$
            }
        }

        return this.envelopedData = this.enveloper.addOriginator(envelop, this.ksConfigManager.getCertificateKeyEntry());
    }

    /** Establece el sobre electr&oacute;nico con el que trabajamos.
     * @param data
     *        Sobre electr&oacute;nico. */
    public void setEnvelopedData(final byte[] data) {
        this.envelopedData = data.clone();
    }

    /** Recupera el envoltorio CMS.
     * @return Envoltorio CMS. */
    public byte[] getEnvelopedData() {
        return this.envelopedData.clone();
    }

    /** Establece los datos que desean ensobrarse.
     * @param data
     *        Datos del sobre. */
    public void setContentData(final byte[] data) {
        this.contentData = data.clone();
    }

    /** Recupera los datos extra&iacute;dos de un envoltorio o preparados para
     * insertarse en el mismo.
     * @return Datos del sobre. */
    public byte[] getContentData() {
        return this.contentData.clone();
    }

    /** Configura un atributo para agregarlo firmado a un envoltorio.
     * @param oid
     *        Identificador del objeto a introducir.
     * @param value
     *        Valor asignado */
    public void addSignedAttribute(final org.ietf.jgss.Oid oid, final byte[] value) {
        this.enveloper.addSignedAttribute(oid, value);
    }

    /** Configura un atributo para agregarlo no firmado a un envoltorio.
     * @param oid
     *        Identificador del atributo a introducir.
     * @param value
     *        Valor asignado */
    public void addUnsignedAttribute(final org.ietf.jgss.Oid oid, final byte[] value) {
        this.enveloper.addUnsignedAttribute(oid, value);
    }

    /** Recupera informaci&oacute;n de una estructura CMS.
     * @param cmsEnvelop
     *        Envoltorio CMS.
     * @return Informaci&oacute;n en texto extra&iacute;da del envoltorio. */
    public String getCMSInfo(final byte[] cmsEnvelop) {
        String cmsInformation;
        try {
            cmsInformation = new CMSInformation().getInformation(cmsEnvelop);
        }
        catch (final AOInvalidFormatException e) {
            LOGGER.severe("Formato de dato no valido: " + e); //$NON-NLS-1$
            return ""; //$NON-NLS-1$
        }
        catch (final Exception e) {
            LOGGER.severe("Error al obtener la informacion del objeto CMS: " + e); //$NON-NLS-1$
            return ""; //$NON-NLS-1$
        }
        return cmsInformation;
    }
}
