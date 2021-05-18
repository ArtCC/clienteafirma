/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.signers.batchV2;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import es.gob.afirma.triphase.server.SignatureService;

/** Gestiona la configuraci&oacute;n espec&iacute;fica del proceso de firma de lotes.*/
public class JSONBatchConfigManager {

	private static final String CONFIG_FILE = "signbatch_config.properties"; //$NON-NLS-1$

	private static final String SYS_PROP_PREFIX = "${"; //$NON-NLS-1$

	private static final String SYS_PROP_SUFIX = "}"; //$NON-NLS-1$

	/** Variable de entorno que determina el directorio en el que buscar el fichero de configuraci&oacute;n. */
	private static final String ENVIRONMENT_VAR_CONFIG_DIR = "clienteafirma.config.path"; //$NON-NLS-1$

	private static final int MAX_CONCURRENT_SIGNS = 10;

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final Properties CONFIG = new Properties();

	private static Boolean CONCURRENT_MODE = null;

	private static Integer CONCURRENT_SIGNS = null;

	private static String[] allowedSources = null;

	private static File tempDir = null;

	private static String saverFileClassName = null;

	static {

		String configDir;
		try {
			configDir = System.getProperty(ENVIRONMENT_VAR_CONFIG_DIR);
		}
		catch (final Exception e) {
			LOGGER.warning(
				"No se ha podido obtener el directorio del fichero de configuracion: " + e //$NON-NLS-1$
			);
			configDir = null;
		}

		// Cargamos la configuracion del servicio
		final Properties configProperties = loadConfigFile(configDir, CONFIG_FILE);

		if (configProperties == null) {
			throw new RuntimeException("No se ha encontrado el fichero de configuracion del servicio"); //$NON-NLS-1$
		}

		for (final String k : configProperties.keySet().toArray(new String[0])) {
			CONFIG.setProperty(k, mapSystemProperties(configProperties.getProperty(k)));
		}
	}

	/**
	 * Indica si esta habilitado el modo de ejecuci&oacute;n concurrente.
	 * @return {@code true} si las firmas se ejecutar&aacute;n de forma concurrente,
	 * {@code false} en caso contrario.
	 */
	public static boolean isConcurrentMode() {
		if (CONCURRENT_MODE == null) {
			CONCURRENT_MODE = Boolean.valueOf(CONFIG.getProperty("concurrentmode")); //$NON-NLS-1$
		}
		return CONCURRENT_MODE.booleanValue();
	}

	/**
	 * Obtiene el n&uacute;mero m&aacute;ximo de firmas que se pueden procesar de forma
	 * concurrente.
	 * @return N&uacute;mero m&aacute;ximo de firmas que se pueden procesar de forma
	 * concurrente.
	 */
	public static int getMaxCurrentSigns() {
		if (CONCURRENT_SIGNS == null) {
			int n = 0;
			try {
				n = Integer.parseInt(CONFIG.getProperty("maxcurrentsigns", Integer.toString(MAX_CONCURRENT_SIGNS))); //$NON-NLS-1$
			}
			catch (final Exception e) {
				LOGGER.warning(
					"El valor configurado como numero maximo de firmas concurrentes no es valido (" + //$NON-NLS-1$
						CONFIG.getProperty("maxcurrentsigns") + "), se usara el valor por defecto (" + MAX_CONCURRENT_SIGNS + "): " + e //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				);
				n = MAX_CONCURRENT_SIGNS;
			}
			CONCURRENT_SIGNS = Integer.valueOf(n > 0 && n <= MAX_CONCURRENT_SIGNS ? n : MAX_CONCURRENT_SIGNS);
		}
		return CONCURRENT_SIGNS.intValue();
	}

	/**
	 * Obtiene el listado de fuentes permitidas como origen de los datos. El formato
	 * de las fuentes puede ser una cadena de texto con una URL (admite '*' como
	 * comod&iacute;n) o la cadena "base64".
	 * @return Listado de fuentes.
	 * @throws IllegalStateException Cuando no se ha indicado ninguna fuente.
	 */
	public static String[] getAllowedSources() {
		if (allowedSources == null) {
			final String sources = CONFIG.getProperty("allowedsources"); //$NON-NLS-1$
			if (sources == null || sources.isEmpty()) {
				throw new IllegalStateException(
					"No se ha definido ningun permiso para la carga de datos" //$NON-NLS-1$
				);
			}
			allowedSources = sources.split(";"); //$NON-NLS-1$
		}
		return allowedSources;
	}

	/**
	 * Obtiene el directorio para el guardado de ficheros temporales.
	 * @return Directorio temporal configurado o el por defecto si no se configur&oacute;
	 * ninguno o no era v&aacute;lido.
	 */
	public static File getTempDir() {
		if (tempDir == null) {
			final String defaultDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
			final File f = new File(CONFIG.getProperty("tmpdir", defaultDir)); //$NON-NLS-1$

			if (f.isDirectory() && f.canRead() && f.canWrite()) {
				return f;
			}
			Logger.getLogger("es.gob.afirma").severe( //$NON-NLS-1$
					"El directorio temporal configurado (" + f.getAbsolutePath() + ") no es valido, se usaran el por defecto: " + defaultDir //$NON-NLS-1$ //$NON-NLS-2$
					);
			tempDir = new File(defaultDir);
		}
		return tempDir;
	}

	/**
	 * Intenta cargar un fichero propiedades del directorio proporcionado o, en caso de
	 * no encontrarlo ah&iacute;, se busca en el <i>classpath</i>.
	 * @param configDir Directorio del fichero de configuraci&oacute;n.
	 * @param configFilename Nombre del fichero de propedades.
	 * @return Propiedades cargadas o {@code null} si no se pudo cargar el fichero.
	 */
	private static Properties loadConfigFile(final String configDir, final String configFilename) {

		LOGGER.info("Se cargara el fichero de configuracion " + configFilename); //$NON-NLS-1$

		Properties configProperties = null;

		if (configDir != null) {
			try {
				final File configFile = new File(configDir, configFilename).getCanonicalFile();
				try (final InputStream configIs = new FileInputStream(configFile);) {
					configProperties = new Properties();
					configProperties.load(configIs);
				}
			}
			catch (final Exception e) {
				LOGGER.warning(
						"No se pudo cargar el fichero de configuracion " + configFilename + //$NON-NLS-1$
						" desde el directorio " + configDir + ": " + e); //$NON-NLS-1$ //$NON-NLS-2$
				configProperties = null;
			}
		}

		if (configProperties == null) {
			LOGGER.info(
				"Se cargara el fichero de configuracion " + configFilename + " desde el CLASSPATH" //$NON-NLS-1$ //$NON-NLS-2$
			);

			try (final InputStream configIs = SignatureService.class.getClassLoader().getResourceAsStream(configFilename);) {
				configProperties = new Properties();
				configProperties.load(configIs);
			}
			catch (final Exception e) {
				LOGGER.warning(
					"No se pudo cargar el fichero de configuracion " + configFilename + " desde el CLASSPATH: " + e //$NON-NLS-1$ //$NON-NLS-2$
				);
				configProperties = null;
			}
		}

		return configProperties;
	}

	/**
	 * Mapea las propiedades del sistema que haya en el texto que se referencien de
	 * la forma: ${propiedad}
	 * @param text Texto en el que se pueden encontrar las referencias a las propiedades
	 * del sistema.
	 * @return Cadena con las particulas traducidas a los valores indicados como propiedades
	 * del sistema. Si no se encuentra la propiedad definida, no se modificar&aacute;
	 */
	private static String mapSystemProperties(final String text) {

		if (text == null) {
			return null;
		}

		int pos = -1;
		int pos2 = 0;
		String mappedText = text;
		while ((pos = mappedText.indexOf(SYS_PROP_PREFIX, pos + 1)) > -1 && pos2 > -1) {
			pos2 = mappedText.indexOf(SYS_PROP_SUFIX, pos + SYS_PROP_PREFIX.length());
			if (pos2 > pos) {
				final String prop = mappedText.substring(pos + SYS_PROP_PREFIX.length(), pos2);
				final String value = System.getProperty(prop, null);
				if (value != null) {
					mappedText = mappedText.replace(SYS_PROP_PREFIX + prop + SYS_PROP_SUFIX, value);
				}
			}
		}
		return mappedText;
	}

	/**
	 * Obtiene el nombre de la clase definida para el gestor para el almacenamiento de firmas
	 * @return Nombre de la clase a usar
	 */
	public static String getSaverFile() {
		if (saverFileClassName == null) {
			final String saverFile = CONFIG.getProperty("signsaver"); //$NON-NLS-1$
			saverFileClassName = saverFile;
			if (saverFile == null || saverFile.isEmpty()) {
				throw new IllegalStateException(
					"No se ha definido ningun gestor para el guardado de firmas" //$NON-NLS-1$
				);
			}
		}
		return saverFileClassName;
	}

	public static Properties getConfig() {
		return CONFIG;
	}
}
