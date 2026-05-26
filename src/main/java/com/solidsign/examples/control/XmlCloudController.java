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
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * [EN]    Form signing endpoint for React — XAdES cloud HSM. properties ignored.
     * [PT-BR] Endpoint de formulário para React — XAdES HSM em nuvem. properties ignorado.
     * [ES]    Endpoint de formulario para React — XAdES HSM en nube. properties ignorado.
     */
    @CrossOrigin
    @PostMapping("/sign/form")
    public ResponseEntity<byte[]> signForm(
            @RequestPart("document")                                                   MultipartFile[] documents,
            @RequestPart("authorization")                                              String authorization,
            @RequestPart("baseUrl")                                                    String baseUrl,
            @RequestPart("cloudCredentials")                                           String cloudCredentials,
            @RequestPart(value = "profile",                         required = false)  String profile,
            @RequestPart(value = "hashAlgorithm",                   required = false)  String hashAlgorithm,
            @RequestPart(value = "signaturePackaging",              required = false)  String signaturePackaging,
            @RequestPart(value = "canonicalizationMethod",          required = false)  String canonicalizationMethod,
            @RequestPart(value = "signatureNodeName",               required = false)  String signatureNodeName,
            @RequestPart(value = "signatureNodeNamespace",          required = false)  String signatureNodeNamespace,
            @RequestPart(value = "isRemoveXPathExclusionFilter",    required = false)  String isRemoveXPathExclusionFilter,
            @RequestPart(value = "isRemoveNamespacePrefixFromNodeNames", required = false) String isRemoveNamespacePrefixFromNodeNames,
            @RequestPart(value = "isSignKeyInfo",                   required = false)  String isSignKeyInfo,
            @RequestPart(value = "signatureNodeId",                 required = false)  String signatureNodeId
    ) throws IOException {
        List<File> tmpFiles = new ArrayList<>();
        java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("solidsign-form-");
        try {
            for (MultipartFile mf : documents) {
                java.nio.file.Path p = tmpDir.resolve(
                        mf.getOriginalFilename() != null ? mf.getOriginalFilename() : "doc");
                mf.transferTo(p);
                tmpFiles.add(p.toFile());
            }
            byte[] zip = service.signWithCloudForm(authorization, baseUrl, cloudCredentials,
                    profile, hashAlgorithm, signaturePackaging,
                    canonicalizationMethod, signatureNodeName, signatureNodeNamespace,
                    isRemoveXPathExclusionFilter, isRemoveNamespacePrefixFromNodeNames,
                    isSignKeyInfo, signatureNodeId, tmpFiles);
            if (zip != null)
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                        .header("Content-Disposition", "attachment; filename=\"signed.zip\"")
                        .body(zip);
            return ResponseEntity.internalServerError().build();
        } finally {
            tmpFiles.forEach(java.io.File::delete);
            tmpDir.toFile().delete();
        }
    }
}
