/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.signers.batch.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.core.misc.http.HttpError;
import es.gob.afirma.core.misc.http.UrlHttpManagerFactory;
import es.gob.afirma.core.misc.http.UrlHttpMethod;
import es.gob.afirma.core.signers.AOPkcs1Signer;
import es.gob.afirma.core.signers.TriphaseData;
import es.gob.afirma.core.signers.TriphaseDataSigner;

/** Cliente del servicio de firma por lote.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
public final class BatchSigner {

	private static final String BATCH_XML_PARAM = "xml"; //$NON-NLS-1$
	private static final String BATCH_JSON_PARAM = "json"; //$NON-NLS-1$
	private static final String BATCH_CRT_PARAM = "certs"; //$NON-NLS-1$
	private static final String BATCH_TRI_PARAM = "tridata"; //$NON-NLS-1$

	private static final String EQU = "="; //$NON-NLS-1$
	private static final String AMP = "&"; //$NON-NLS-1$

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	private BatchSigner() {
		// No instanciable
	}

	/** Procesa un lote de firmas.
	 * Los lotes deben proporcionase definidos en un fichero XML con el siguiente esquema XSD:
	 * <pre>
	 * &lt;xs:schema attributeFormDefault="unqualified" elementFormDefault="qualified" xmlns:xs="http://www.w3.org/2001/XMLSchema"&gt;
  	 * &lt;xs:element name="signbatch"&gt;
	 *     &lt;xs:complexType&gt;
	 *       &lt;xs:sequence&gt;
	 *         &lt;xs:element name="singlesign" maxOccurs="unbounded" minOccurs="1"&gt;
	 *           &lt;xs:complexType&gt;
	 *             &lt;xs:sequence&gt;
	 *               &lt;xs:element type="xs:string" name="datasource"/&gt;
	 *               &lt;xs:element name="format"&gt;
	 *                 &lt;xs:simpleType&gt;
	 *                   &lt;xs:restriction base="xs:string"&gt;
	 *                     &lt;xs:enumeration value="XAdES"/&gt;
	 *                     &lt;xs:enumeration value="CAdES"/&gt;
	 *                     &lt;xs:enumeration value="PAdES"/&gt;
	 *                   &lt;/xs:restriction&gt;
	 *                 &lt;/xs:simpleType&gt;
	 *               &lt;/xs:element&gt;
	 *               &lt;xs:element name="suboperation"&gt;
	 *                 &lt;xs:simpleType&gt;
	 *                   &lt;xs:restriction base="xs:string"&gt;
	 *                     &lt;xs:enumeration value="sign"/&gt;
	 *                     &lt;xs:enumeration value="cosign"/&gt;
	 *                   &lt;/xs:restriction&gt;
	 *                 &lt;/xs:simpleType&gt;
	 *               &lt;/xs:element&gt;
	 *               &lt;xs:element name="extraparams"&gt;
	 *                 &lt;xs:simpleType&gt;
	 *                  &lt;xs:restriction  base="xs:base64Binary" /&gt;
	 *                 &lt;/xs:simpleType&gt;
	 *               &lt;/xs:element&gt;
	 *               &lt;xs:element name="signsaver"&gt;
	 *                 &lt;xs:complexType&gt;
	 *                   &lt;xs:sequence&gt;
	 *                     &lt;xs:element type="xs:string" name="class"/&gt;
	 *                     &lt;xs:element name="config"&gt;
	 *                       &lt;xs:simpleType&gt;
	 *                         &lt;xs:restriction  base="xs:base64Binary" /&gt;
	 *                       &lt;/xs:simpleType&gt;
	 *                     &lt;/xs:element&gt;
	 *                   &lt;/xs:sequence&gt;
	 *                 &lt;/xs:complexType&gt;
	 *               &lt;/xs:element&gt;
	 *             &lt;/xs:sequence&gt;
	 *             &lt;xs:attribute type="xs:string" name="id" use="required"/&gt;
	 *           &lt;/xs:complexType&gt;
	 *         &lt;/xs:element&gt;
	 *       &lt;/xs:sequence&gt;
	 *       &lt;xs:attribute type="xs:integer" name="concurrenttimeout" use="optional"/&gt;
	 *       &lt;xs:attribute type="xs:string" name="stoponerror" use="optional"/&gt;
	 *         &lt;xs:simpleType&gt;
	 *           &lt;xs:restriction base="xs:string"&gt;
	 *             &lt;xs:enumeration value="true"/&gt;
	 *             &lt;xs:enumeration value="false"/&gt;
	 *           &lt;/xs:restriction&gt;
	 *         &lt;/xs:simpleType&gt;
	 *       &lt;/xs:attribute&gt;
	 *       &lt;xs:attribute type="xs:string" name="algorithm" use="required"&gt;
	 *         &lt;xs:simpleType&gt;
	 *           &lt;xs:restriction base="xs:string"&gt;
	 *             &lt;xs:enumeration value="SHA1withRSA"/&gt;
	 *             &lt;xs:enumeration value="SHA256withRSA"/&gt;
	 *             &lt;xs:enumeration value="SHA384withRSA"/&gt;
	 *             &lt;xs:enumeration value="SHA512withRSA"/&gt;
	 *           &lt;/xs:restriction&gt;
	 *         &lt;/xs:simpleType&gt;
	 *       &lt;xs:attribute&gt;
	 *     &lt;/xs:complexType&gt;
	 *   &lt;/xs:element&gt;
	 * &lt;/xs:schema&gt;
	 * </pre>
	 * Un ejemplo de definici&oacute;n XML de lote de firmas podr&iacute;a ser
	 * este (ejemplo con dos firmas en el lote):
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8" ?&gt;
	 * &lt;signbatch stoponerror="true" algorithm="SHA1withRSA"&gt;
	 *  &lt;singlesign id="f8526f7b-d30a-4720-9e35-fe3494217944"&gt;
	 *   &lt;datasource&gt;http://google.com&lt;/datasource&gt;
	 *   &lt;format&gt;XAdES&lt;/format&gt;
	 *   &lt;suboperation&gt;sign&lt;/suboperation&gt;
	 *   &lt;extraparams&gt;Iw0KI1RodSBBdW[...]QNCg==&lt;/extraparams&gt;
	 *   &lt;signsaver&gt;
	 *    &lt;class&gt;es.gob.afirma.signers.batch.SignSaverFile&lt;/class&gt;
	 *    &lt;config&gt;Iw0KI1RodSBBdWcgMT[...]wNCg==&lt;/config&gt;
	 *   &lt;/signsaver&gt;
	 *  &lt;/singlesign&gt;
	 *  &lt;singlesign id="0e9cc5de-63ee-45ee-ae02-4a6591ab9a46"&gt;
	 *   &lt;datasource&gt;SG9sYSBNdW5kbw==&lt;/datasource&gt;
	 *   &lt;format&gt;CAdES&lt;/format&gt;
	 *   &lt;suboperation&gt;sign&lt;/suboperation&gt;
	 *   &lt;extraparams&gt;Iw0KI1RodSBBdWc[...]NCg==&lt;/extraparams&gt;
	 *   &lt;signsaver&gt;
	 *    &lt;class&gt;es.gob.afirma.signers.batch.SignSaverFile&lt;/class&gt;
	 *    &lt;config&gt;Iw0KI1RodSBBdWcgMTM[...]Cg==&lt;/config&gt;
	 *   &lt;/signsaver&gt;
	 *  &lt;/singlesign&gt;
	 * &lt;/signbatch&gt;
	 * </pre>
	 * @param batchB64 XML de definici&oacute;n del lote de firmas.
	 * @param batchPresignerUrl URL del servicio remoto de preproceso de lotes de firma.
	 * @param batchPostSignerUrl URL del servicio remoto de postproceso de lotes de firma.
	 * @param certificates Cadena de certificados del firmante.
	 * @param pk Clave privada para realizar las firmas cliente.
	 * @return Registro del resultado general del proceso por lote, en un XML con este esquema:
	 * <pre>
	 * &lt;xs:schema attributeFormDefault="unqualified" elementFormDefault="qualified" xmlns:xs="http://www.w3.org/2001/XMLSchema"&gt;
	 *  &lt;xs:element name="signs"&gt;
	 *    &lt;xs:complexType&gt;
	 *      &lt;xs:sequence&gt;
	 *        &lt;xs:element name="sign" maxOccurs="unbounded" minOccurs="1"&gt;
	 *          &lt;xs:complexType&gt;
	 *            &lt;xs:sequence&gt;
	 *              &lt;xs:element name="result"&gt;
	 *                &lt;xs:simpleType&gt;
	 *                  &lt;xs:restriction base="xs:string"&gt;
	 *                    &lt;xs:enumeration value="OK"/&gt;
	 *                    &lt;xs:enumeration value="KO"/&gt;
	 *                    &lt;xs:enumeration value="NP"/&gt;
	 *                  &lt;/xs:restriction&gt;
	 *                &lt;/xs:simpleType&gt;
	 *              &lt;/xs:element&gt;
	 *              &lt;xs:element type="xs:string" name="reason" minOccurs="0"/&gt;
	 *            &lt;/xs:sequence&gt;
	 *            &lt;xs:attribute type="xs:string" name="id" use="required"/&gt;
	 *          &lt;/xs:complexType&gt;
	 *        &lt;/xs:element&gt;
	 *      &lt;/xs:sequence&gt;
	 *    &lt;/xs:complexType&gt;
	 *  &lt;/xs:element&gt;
	 * &lt;/xs:schema&gt;
	 * </pre>
	 * @throws IOException Si hay problemas de red o en el tratamiento de datos.
	 * @throws CertificateEncodingException Si los certificados proporcionados no son v&aacute;lidos.
	 * @throws AOException Si hay errores en las firmas cliente. */

	public static String sign(final String batchB64,
			final String batchPresignerUrl,
			final String batchPostSignerUrl,
			final Certificate[] certificates,
			final PrivateKey pk) throws CertificateEncodingException,
										IOException,
										AOException {

		if (batchB64 == null || batchB64.isEmpty()) {
			throw new IllegalArgumentException("El lote de firma no puede ser nulo ni vacio"); //$NON-NLS-1$
		}
		if (batchPresignerUrl == null || batchPresignerUrl.isEmpty()) {
			throw new IllegalArgumentException(
					"La URL de preproceso de lotes no puede se nula ni vacia" //$NON-NLS-1$
					);
		}
		if (batchPostSignerUrl == null || batchPostSignerUrl.isEmpty()) {
			throw new IllegalArgumentException(
					"La URL de postproceso de lotes no puede ser nula ni vacia" //$NON-NLS-1$
					);
		}
		if (certificates == null || certificates.length < 1) {
			throw new IllegalArgumentException(
					"La cadena de certificados del firmante no puede ser nula ni vacia" //$NON-NLS-1$
					);
		}

		final String batchUrlSafe = batchB64.replace("+", "-").replace("/",  "_");  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$

		byte[] ret;
		try {
			ret = UrlHttpManagerFactory.getInstalledManager().readUrl(
					batchPresignerUrl + "?" + //$NON-NLS-1$
							BATCH_XML_PARAM + EQU + batchUrlSafe + AMP +
							BATCH_CRT_PARAM + EQU + getCertChainAsBase64(certificates),
							UrlHttpMethod.POST
					);
		}
		catch (final HttpError e) {
			LOGGER.warning("El servicio de firma devolvio un  error durante la prefirma: " + e.getResponseDescription()); //$NON-NLS-1$
			throw e;
		}

		final TriphaseData td1 = TriphaseData.parser(ret);

		// El cliente hace los PKCS#1 generando TD2, que envia de nuevo al servidor
		final TriphaseData td2 = TriphaseDataSigner.doSign(
				new AOPkcs1Signer(),
				getAlgorithmForXML(batchB64),
				pk,
				certificates,
				td1,
				null // Sin ExtraParams para el PKCS#1 en lotes
				);

		// Llamamos al servidor de nuevo para el postproceso
		try {
			ret = UrlHttpManagerFactory.getInstalledManager().readUrl(
					batchPostSignerUrl + "?" + //$NON-NLS-1$
							BATCH_XML_PARAM + EQU + batchUrlSafe + AMP +
							BATCH_CRT_PARAM + EQU + getCertChainAsBase64(certificates) + AMP +
							BATCH_TRI_PARAM + EQU + Base64.encode(td2.toString().getBytes(DEFAULT_CHARSET), true),
							UrlHttpMethod.POST
					);
		}
		catch (final HttpError e) {
			LOGGER.warning("El servicio de firma devolvio un  error durante la postfirma: " + e.getResponseDescription()); //$NON-NLS-1$
			throw e;
		}

		return new String(ret, DEFAULT_CHARSET);
	}


	public static String sign(final String batchB64,
			                  final String batchPresignerUrl,
			                  final String batchPostSignerUrl,
			                  final Certificate[] certificates,
			                  final PrivateKey pk,
			                  final boolean jsonBatch) throws CertificateEncodingException,
			                                              IOException,
			                                              AOException {
		if (batchB64 == null || batchB64.isEmpty()) {
			throw new IllegalArgumentException("El lote de firma no puede ser nulo ni vacio"); //$NON-NLS-1$
		}
		if (batchPresignerUrl == null || batchPresignerUrl.isEmpty()) {
			throw new IllegalArgumentException(
				"La URL de preproceso de lotes no puede se nula ni vacia" //$NON-NLS-1$
			);
		}
		if (batchPostSignerUrl == null || batchPostSignerUrl.isEmpty()) {
			throw new IllegalArgumentException(
				"La URL de postproceso de lotes no puede ser nula ni vacia" //$NON-NLS-1$
			);
		}
		if (certificates == null || certificates.length < 1) {
			throw new IllegalArgumentException(
				"La cadena de certificados del firmante no puede ser nula ni vacia" //$NON-NLS-1$
			);
		}

		final String batchUrlSafe = batchB64.replace("+", "-").replace("/",  "_");  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
		final String algorithm;
		byte[] ret;
		String batchType;
		if (jsonBatch) {
			batchType = BATCH_JSON_PARAM;
			algorithm = getAlgorithmForJSON(batchB64);
		} else {
			batchType = BATCH_XML_PARAM;
			algorithm = getAlgorithmForXML(batchB64);
		}
		try {
			ret = UrlHttpManagerFactory.getInstalledManager().readUrl(
				batchPresignerUrl + "?" + //$NON-NLS-1$
					batchType + EQU + batchUrlSafe + AMP +
					BATCH_CRT_PARAM + EQU + getCertChainAsBase64(certificates),
				UrlHttpMethod.POST
			);
		}
		catch (final HttpError e) {
			LOGGER.warning("El servicio de firma devolvio un  error durante la prefirma: " + e.getResponseDescription()); //$NON-NLS-1$
			throw e;
		}

		final TriphaseData td1 = TriphaseData.parserFromJSON(ret);

		// El cliente hace los PKCS#1 generando TD2, que envia de nuevo al servidor
		final TriphaseData td2 = TriphaseDataSigner.doSign(
			new AOPkcs1Signer(),
			algorithm,
			pk,
			certificates,
			td1,
			null // Sin ExtraParams para el PKCS#1 en lotes
		);

		// Llamamos al servidor de nuevo para el postproceso
		try {
			ret = UrlHttpManagerFactory.getInstalledManager().readUrl(
				batchPostSignerUrl + "?" + //$NON-NLS-1$
					batchType + EQU + batchUrlSafe + AMP +
					BATCH_CRT_PARAM + EQU + getCertChainAsBase64(certificates) + AMP +
					BATCH_TRI_PARAM + EQU + Base64.encode(td2.toStringJSONFormat().getBytes(DEFAULT_CHARSET), true),
				UrlHttpMethod.POST
			);
		}
		catch (final HttpError e) {
			LOGGER.warning("El servicio de firma devolvio un  error durante la postfirma: " + e.getResponseDescription()); //$NON-NLS-1$
			throw e;
		}

		return new String(ret, DEFAULT_CHARSET);
	}

	private static String getCertChainAsBase64(final Certificate[] certChain) throws CertificateEncodingException {
		final StringBuilder sb = new StringBuilder();
		for (final Certificate cert : certChain) {
			sb.append(Base64.encode(cert.getEncoded(), true));
			sb.append(";"); //$NON-NLS-1$
		}
		final String ret = sb.toString();

		// Quitamos el ";" final
		return ret.substring(0, ret.length()-1);
	}

	/**
	 * Obtiene el algoritmo para la petici&oacute;n de formato XML
	 * @param batch datos de la petici&oacute;n
	 * @return algoritmo a usar
	 * @throws IOException error en caso de que no se lea correctamente la petici&oacute;n
	 */
	private static String getAlgorithmForXML(final String batch) throws IOException {
		final byte[] xml =  Base64.decode(batch.replace("-", "+").replace("_", "/")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		final Document doc;
		try (
			final InputStream is = new ByteArrayInputStream(xml);
		) {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		}
		catch (final Exception e) {
			LOGGER.severe(
				"Error al cargar el fichero XML de lote: " + e + "\n" + new String(xml, DEFAULT_CHARSET) //$NON-NLS-1$ //$NON-NLS-2$
			);
			throw new IOException("Error al cargar el fichero XML de lote: " + e, e); //$NON-NLS-1$
		}

		final Node signBatchNode = doc.getDocumentElement();
		if (!"signbatch".equalsIgnoreCase(signBatchNode.getNodeName())) { //$NON-NLS-1$
			throw new IllegalArgumentException("No se encontro el nodo 'signbatch' en el XML proporcionado"); //$NON-NLS-1$
		}

		final NamedNodeMap nnm = signBatchNode.getAttributes();
		if (nnm != null) {
			final Node tmpNode = nnm.getNamedItem("algorithm"); //$NON-NLS-1$
			if (tmpNode != null) {
				return tmpNode.getNodeValue();
			}
			throw new IllegalArgumentException(
				"El nodo 'signbatch' debe contener al manos el atributo de algoritmo" //$NON-NLS-1$
			);
		}
		throw new IllegalArgumentException(
			"El nodo 'signbatch' debe contener al manos el atributo de algoritmo" //$NON-NLS-1$
		);

	}

	/**
	 * Obtiene el algoritmo para la petici&oacute;n de formato JSON
	 * @param batch datos de la petici&oacute;n
	 * @return algoritmo a usar
	 * @throws IOException error en caso de que no se lea correctamente la petici&oacute;n
	 */
	private static String getAlgorithmForJSON(final String batch) throws IOException  {

		JSONObject jsonObject = null;
		final String convertedJson = new String(Base64.decode(batch), DEFAULT_CHARSET);
		try {
			jsonObject = new JSONObject(convertedJson);
		}catch (final JSONException jsonEx){
			LOGGER.severe("Error al parsear JSON"); //$NON-NLS-1$
			throw new JSONException(
					"El JSON de definicion de lote de firmas no esta formado correctamente" //$NON-NLS-1$
				);
		}

		if (jsonObject.has("algorithm")){ //$NON-NLS-1$
			return jsonObject.getString("algorithm"); //$NON-NLS-1$
		}

		throw new IllegalArgumentException(
				"El nodo 'signbatch' debe contener al manos el atributo de algoritmo" //$NON-NLS-1$
			);
	}

}
