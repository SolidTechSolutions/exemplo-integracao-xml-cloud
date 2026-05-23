package com.solidsign.examples.control;

import com.solidsign.examples.service.XmlCloudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * [EN]    REST controller that triggers XAdES (XML) signing using cloud HSM credentials.
 *         Scans an input folder for XML files and signs all of them.
 *
 * [PT-BR] Controller REST que dispara a assinatura XAdES (XML) usando credenciais de HSM em nuvem.
 *         Varre uma pasta de entrada por arquivos XML e assina todos eles.
 *
 * [ES]    Controller REST que activa la firma XAdES (XML) usando credenciales de HSM en la nube.
 *         Escanea una carpeta de entrada por archivos XML y firma todos ellos.
 */
@RestController
@RequestMapping("/api/xml")
public class XmlCloudController {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlCloudController.class);

    @Autowired
    private XmlCloudService service;

    // [EN]    Path to the folder containing XML files to sign
    // [PT-BR] Caminho para a pasta contendo os arquivos XML a assinar
    // [ES]    Ruta a la carpeta que contiene los archivos XML a firmar
    @Value("${solidsign.batch.input-path}")
    private String inputPath;

    // [EN]    Path to the folder where the signed ZIP will be written
    // [PT-BR] Caminho para a pasta onde o ZIP assinado será gravado
    // [ES]    Ruta a la carpeta donde se escribirá el ZIP firmado
    @Value("${solidsign.batch.output-path}")
    private String outputPath;

    /**
     * [EN]    Signs all XML files inside the configured input folder using XAdES Cloud HSM.
     *         Returns the path to the output ZIP on success.
     *
     * [PT-BR] Assina todos os XMLs da pasta de entrada configurada usando XAdES Cloud HSM.
     *         Retorna o caminho do ZIP de saída em caso de sucesso.
     *
     * [ES]    Firma todos los archivos XML de la carpeta de entrada configurada con XAdES Cloud HSM.
     *         Devuelve la ruta del ZIP de salida en caso de éxito.
     */
    @PostMapping("/sign-cloud")
    public ResponseEntity<String> signFolder() throws IOException {
        File folder = new File(inputPath);
        if (!folder.exists() || !folder.isDirectory()) {
            // [EN]    Configured input path is invalid or not a directory
            // [PT-BR] O caminho de entrada configurado é inválido ou não é um diretório
            // [ES]    La ruta de entrada configurada es inválida o no es un directorio
            return ResponseEntity.badRequest().body("Invalid path: " + inputPath);
        }
        File[] files = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".xml"));
        if (files == null || files.length == 0) {
            // [EN]    No XML files found in the input folder
            // [PT-BR] Nenhum arquivo XML encontrado na pasta de entrada
            // [ES]    No se encontraron archivos XML en la carpeta de entrada
            return ResponseEntity.ok("No XML files found in " + inputPath);
        }
        LOGGER.info("Found {} XML(s) for cloud signing.", files.length);
        String result = service.signWithCloud(Arrays.asList(files), outputPath);
        return result != null
                ? ResponseEntity.ok("ZIP at: " + result)
                : ResponseEntity.internalServerError().body("Failed. Check logs.");
    }
}
