package com.solidsign.examples.service;

import com.solidsign.examples.response.SignResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.List;
import java.util.zip.*;

/**
 * [EN]    Service that signs XML (XAdES) documents using a cloud HSM certificate.
 *         Calls the SolidSign API endpoint: POST /solidsign/dsig/xml/sign-hsm-cloud
 *
 * [PT-BR] Serviço que assina documentos XML (XAdES) usando um certificado HSM em nuvem.
 *         Chama o endpoint da API SolidSign: POST /solidsign/dsig/xml/sign-hsm-cloud
 *
 * [ES]    Servicio que firma documentos XML (XAdES) usando un certificado HSM en la nube.
 *         Llama al endpoint de la API SolidSign: POST /solidsign/dsig/xml/sign-hsm-cloud
 */
@Service
public class XmlCloudService {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlCloudService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    // [EN]    Base URL of the SolidSign API
    // [PT-BR] URL base da API SolidSign
    // [ES]    URL base de la API SolidSign
    @Value("${solidsign.api.base-url}")
    private String baseUrl;

    // [EN]    Authorization header value (Bearer token)
    // [PT-BR] Valor do header Authorization (token Bearer)
    // [ES]    Valor del header Authorization (token Bearer)
    @Value("${solidsign.api.authorization}")
    private String authorization;

    // [EN]    Signature profile (e.g. ADRB, ADRT, ADRC, ADRA)
    // [PT-BR] Perfil de assinatura (ex: ADRB, ADRT, ADRC, ADRA)
    // [ES]    Perfil de firma (p.ej. ADRB, ADRT, ADRC, ADRA)
    @Value("${solidsign.sig.profile}")
    private String profile;

    // [EN]    Hash algorithm (SHA256, SHA384, SHA512)
    // [PT-BR] Algoritmo de hash (SHA256, SHA384, SHA512)
    // [ES]    Algoritmo de hash (SHA256, SHA384, SHA512)
    @Value("${solidsign.sig.hashAlgorithm}")
    private String hashAlgorithm;

    // [EN]    Signature packaging (ENVELOPED, ENVELOPING, DETACHED)
    // [PT-BR] Empacotamento da assinatura (ENVELOPED, ENVELOPING, DETACHED)
    // [ES]    Empaquetado de la firma (ENVELOPED, ENVELOPING, DETACHED)
    @Value("${solidsign.sig.signaturePackaging}")
    private String signaturePackaging;

    // [EN]    Local name of the XML node to sign
    // [PT-BR] Nome local do nó XML a assinar
    // [ES]    Nombre local del nodo XML a firmar
    @Value("${solidsign.sig.signatureNodeName}")
    private String signatureNodeName;

    // [EN]    Namespace URI of the XML node to sign
    // [PT-BR] URI de namespace do nó XML a assinar
    // [ES]    URI de namespace del nodo XML a firmar
    @Value("${solidsign.sig.signatureNodeNamespace}")
    private String signatureNodeNamespace;

    // [EN]    Canonicalization algorithm name (INCLUSIVE, EXCLUSIVE, INCLUSIVE_WITH_COMMENTS, EXCLUSIVE_WITH_COMMENTS)
    // [PT-BR] Nome do algoritmo de canonicalização (INCLUSIVE, EXCLUSIVE, INCLUSIVE_WITH_COMMENTS, EXCLUSIVE_WITH_COMMENTS)
    // [ES]    Nombre del algoritmo de canonicalización (INCLUSIVE, EXCLUSIVE, INCLUSIVE_WITH_COMMENTS, EXCLUSIVE_WITH_COMMENTS)
    @Value("${solidsign.sig.canonicalizationMethod}")
    private String canonicalizationMethod;

    // [EN]    Remove XPath exclusion filter from the signed document (false recommended for standard use)
    // [PT-BR] Remover filtro de exclusão XPath do documento assinado (false recomendado para uso padrão)
    // [ES]    Eliminar filtro de exclusión XPath del documento firmado (false recomendado para uso estándar)
    @Value("${solidsign.sig.isRemoveXPathExclusionFilter}")
    private String isRemoveXPathExclusionFilter;

    // [EN]    Remove namespace prefix from node names before signing (false recommended for standard use)
    // [PT-BR] Remover prefixo de namespace dos nomes de nó antes de assinar (false recomendado para uso padrão)
    // [ES]    Eliminar prefijo de namespace de los nombres de nodo antes de firmar (false recomendado para uso estándar)
    @Value("${solidsign.sig.isRemoveNamespacePrefixFromNodeNames}")
    private String isRemoveNamespacePrefixFromNodeNames;

    // [EN]    Include KeyInfo element in the signature (false recommended for standard use)
    // [PT-BR] Incluir elemento KeyInfo na assinatura (false recomendado para uso padrão)
    // [ES]    Incluir elemento KeyInfo en la firma (false recomendado para uso estándar)
    @Value("${solidsign.sig.isSignKeyInfo}")
    private String isSignKeyInfo;

    // [EN]    Optional: ID of the specific XML node to sign (leave blank to sign by name/namespace only)
    // [PT-BR] Opcional: ID do nó XML específico a assinar (deixe em branco para assinar apenas por nome/namespace)
    // [ES]    Opcional: ID del nodo XML específico a firmar (deje en blanco para firmar solo por nombre/namespace)
    // @Value("${solidsign.sig.signatureNodeId:}")
    // private String signatureNodeId;

    // [EN]    Cloud HSM credentials as JSON (uuidCert, hsmToken, hsmServiceUrl)
    // [PT-BR] Credenciais do HSM em nuvem como JSON (uuidCert, hsmToken, hsmServiceUrl)
    // [ES]    Credenciales del HSM en la nube como JSON (uuidCert, hsmToken, hsmServiceUrl)
    @Value("${solidsign.cloud.credentials}")
    private String cloudCredentials;

    /**
     * [EN]    Signs the given XML files using a cloud HSM and returns the path of the output ZIP.
     * [PT-BR] Assina os arquivos XML informados usando um HSM em nuvem e retorna o caminho do ZIP de saída.
     * [ES]    Firma los archivos XML dados con un HSM en la nube y devuelve la ruta del ZIP de salida.
     *
     * @param xmlFiles
     *   [EN]    list of XML files to sign
     *   [PT-BR] lista de arquivos XML a assinar
     *   [ES]    lista de archivos XML a firmar
     * @param outputDir
     *   [EN]    destination folder for the output ZIP
     *   [PT-BR] pasta de destino para o ZIP de saída
     *   [ES]    carpeta de destino para el ZIP de salida
     * @return
     *   [EN]    path of the generated ZIP, or null on error
     *   [PT-BR] caminho do ZIP gerado, ou null em caso de erro
     *   [ES]    ruta del ZIP generado, o null en caso de error
     */
    public String signWithCloud(List<File> xmlFiles, String outputDir) throws IOException {
        LOGGER.info("Starting XAdES Cloud signing for {} XML(s).", xmlFiles.size());

        // [EN]    Build the full endpoint URL from the base URL
        // [PT-BR] Constrói a URL completa do endpoint a partir da URL base
        // [ES]    Construye la URL completa del endpoint a partir de la URL base
        String url = baseUrl + "/solidsign/dsig/xml/sign-hsm-cloud";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", authorization);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // [EN]    Attach each XML indexed as document[0], document[1], ...
        // [PT-BR] Anexa cada XML indexado como document[0], document[1], ...
        // [ES]    Adjunta cada XML indexado como document[0], document[1], ...
        for (int i = 0; i < xmlFiles.size(); i++) {
            body.add("document[" + i + "]", new FileSystemResource(xmlFiles.get(i)));
        }

        body.add("cloudCredentials",       cloudCredentials);
        body.add("profile",                profile);
        body.add("hashAlgorithm",          hashAlgorithm);
        body.add("signaturePackaging",     signaturePackaging);
        body.add("signatureNodeName",      signatureNodeName);
        body.add("signatureNodeNamespace", signatureNodeNamespace);
        body.add("canonicalizationMethod", canonicalizationMethod);

        // [EN]    Optional: per-document node ID (uncomment if targeting a specific node by ID)
        // [PT-BR] Opcional: ID do nó por documento (descomente se for assinar um nó específico por ID)
        // [ES]    Opcional: ID del nodo por documento (descomente para firmar un nodo específico por ID)
        // body.add("signatureNodeId", signatureNodeId);

        // [EN]    XPath filter and namespace prefix options
        // [PT-BR] Opções de filtro XPath e prefixo de namespace
        // [ES]    Opciones de filtro XPath y prefijo de namespace
        body.add("isRemoveXPathExclusionFilter",        isRemoveXPathExclusionFilter);
        body.add("isRemoveNamespacePrefixFromNodeNames", isRemoveNamespacePrefixFromNodeNames);
        body.add("isSignKeyInfo",                        isSignKeyInfo);

        try {
            ResponseEntity<SignResponse> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), SignResponse.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                byte[] zip = downloadAndZip(resp.getBody(), xmlFiles);
                new File(outputDir).mkdirs();
                String out = outputDir + "/signed_xml_cloud_" + System.currentTimeMillis() + ".zip";
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(zip);
                }
                LOGGER.info("XAdES Cloud signing complete. Output: {}", out);
                return out;
            }
        } catch (HttpStatusCodeException e) {
            LOGGER.error("SolidSign API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            LOGGER.error("Unexpected error during XAdES Cloud signing: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * [EN]    Downloads each signed document from the SolidSign response links and packages them into a ZIP.
     * [PT-BR] Baixa cada documento assinado dos links da resposta SolidSign e os empacota em um ZIP.
     * [ES]    Descarga cada documento firmado de los enlaces de respuesta SolidSign y los empaqueta en un ZIP.
     */
    private byte[] downloadAndZip(SignResponse resp, List<File> originals) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorization);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            for (int i = 0; i < resp.documents.size(); i++) {
                String downloadUrl = resp.documents.get(i).links.stream()
                        .filter(l -> "self".equals(l.rel))
                        .findFirst()
                        .map(l -> l.href)
                        .orElse(null);
                if (downloadUrl == null) continue;
                ResponseEntity<byte[]> r = restTemplate.exchange(
                        downloadUrl, HttpMethod.GET, entity, byte[].class);
                if (r.getStatusCode() == HttpStatus.OK) {
                    zos.putNextEntry(new ZipEntry("signed_" + originals.get(i).getName()));
                    zos.write(r.getBody());
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    // ─── Form endpoint (all params from request, properties ignored) ──────────

    /**
     * [EN]    Signs XML documents via XAdES cloud HSM with all parameters supplied by the caller.
     * [PT-BR] Assina documentos XML via XAdES HSM em nuvem com todos os parâmetros do chamador.
     * [ES]    Firma documentos XML vía XAdES HSM en la nube con todos los parámetros del llamador.
     *
     * @return ZIP bytes with signed documents, or null on error
     */
    public byte[] signWithCloudForm(String auth, String apiBaseUrl, String cloudCredentials,
                                     String profile, String hashAlgorithm,
                                     String signaturePackaging,
                                     String canonicalizationMethod,
                                     String signatureNodeName, String signatureNodeNamespace,
                                     String isRemoveXPathExclusionFilter,
                                     String isRemoveNamespacePrefixFromNodeNames,
                                     String isSignKeyInfo, String signatureNodeId,
                                     List<File> files) throws IOException {
        LOGGER.info("XAdES Cloud form signing for {} file(s).", files.size());
        String signUrl = apiBaseUrl + "/solidsign/dsig/xml/sign-hsm-cloud";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", auth);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (int i = 0; i < files.size(); i++) body.add("document[" + i + "]", new FileSystemResource(files.get(i)));
        body.add("cloudCredentials", cloudCredentials);
        if (profile != null && !profile.isBlank())                       body.add("profile",            profile);
        if (hashAlgorithm != null && !hashAlgorithm.isBlank())           body.add("hashAlgorithm",      hashAlgorithm);
        if (signaturePackaging != null && !signaturePackaging.isBlank()) body.add("signaturePackaging", signaturePackaging);
        if (signatureNodeName != null && !signatureNodeName.isBlank())   body.add("signatureNodeName",  signatureNodeName);
        if (signatureNodeNamespace != null && !signatureNodeNamespace.isBlank()) body.add("signatureNodeNamespace", signatureNodeNamespace);
        if (canonicalizationMethod != null && !canonicalizationMethod.isBlank()) body.add("canonicalizationMethod", canonicalizationMethod);
        if (signatureNodeId != null && !signatureNodeId.isBlank())       body.add("signatureNodeId",    signatureNodeId);
        if (isRemoveXPathExclusionFilter != null)        body.add("isRemoveXPathExclusionFilter",        isRemoveXPathExclusionFilter);
        if (isRemoveNamespacePrefixFromNodeNames != null) body.add("isRemoveNamespacePrefixFromNodeNames", isRemoveNamespacePrefixFromNodeNames);
        if (isSignKeyInfo != null)                       body.add("isSignKeyInfo",                        isSignKeyInfo);
        try {
            ResponseEntity<SignResponse> resp = restTemplate.postForEntity(
                    signUrl, new HttpEntity<>(body, headers), SignResponse.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                SignResponse signResp = resp.getBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                    HttpHeaders dh = new HttpHeaders();
                    dh.set("Authorization", auth);
                    HttpEntity<Void> de = new HttpEntity<>(dh);
                    for (int i = 0; i < signResp.documents.size(); i++) {
                        String dlUrl = signResp.documents.get(i).links.stream()
                                .filter(l -> "self".equals(l.rel)).findFirst()
                                .map(l -> l.href).orElse(null);
                        if (dlUrl == null) continue;
                        ResponseEntity<byte[]> r = restTemplate.exchange(
                                dlUrl, HttpMethod.GET, de, byte[].class);
                        if (r.getStatusCode() == HttpStatus.OK) {
                            zos.putNextEntry(new ZipEntry("signed_" + files.get(i).getName()));
                            zos.write(r.getBody());
                            zos.closeEntry();
                        }
                    }
                }
                return baos.toByteArray();
            }
        } catch (HttpStatusCodeException e) {
            LOGGER.error("SolidSign API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            LOGGER.error("Unexpected error in XAdES Cloud form signing: {}", e.getMessage(), e);
        }
        return null;
    }
}
