/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.signers.batchV2.serverV2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.core.signers.TriphaseData;

final class JSONBatchServerUtil {

	private JSONBatchServerUtil() {
		// No instanciable
	}

	static TriphaseData getTriphaseData(final byte[] triphaseDataAsUrlSafeBase64) throws IOException {
		return TriphaseData.parserFromJSON(
			Base64.decode(triphaseDataAsUrlSafeBase64, 0, triphaseDataAsUrlSafeBase64.length, true)
		);
	}

	static byte[] getSignBatchConfig(final byte[] jsonAsUrlSafeBase64) throws IOException {
		if (jsonAsUrlSafeBase64 == null) {
			throw new IllegalArgumentException(
				"La definicion de lote no puede ser nula" //$NON-NLS-1$
			);
		}
		final byte[] json = Base64.isBase64(jsonAsUrlSafeBase64) ?
			Base64.decode(jsonAsUrlSafeBase64, 0, jsonAsUrlSafeBase64.length, true) :
				jsonAsUrlSafeBase64;

		return json;
	}

	static X509Certificate[] getCertificates(final String certListUrlSafeBase64) throws CertificateException,
	                                                                                    IOException {
		if (certListUrlSafeBase64 == null) {
			throw new IllegalArgumentException(
				"La lista de certificados no puede ser nula" //$NON-NLS-1$
			);
		}

		final String[] certs = certListUrlSafeBase64.split(";"); //$NON-NLS-1$
		final CertificateFactory cf = CertificateFactory.getInstance("X.509"); //$NON-NLS-1$
		final List<X509Certificate> ret = new ArrayList<>(certs.length);
		for (final String cert : certs) {
			ret.add(
					(X509Certificate) cf.generateCertificate(
							new ByteArrayInputStream(Base64.decode(cert, true))
							)
					);
		}
		return ret.toArray(new X509Certificate[0]);
	}
}
