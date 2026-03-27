// package com.exemple.nexrag.service.rag.ingestion.util;

// import org.springframework.stereotype.Component;
// import org.springframework.web.multipart.MultipartFile;

// import java.util.Set;

// /**
//  * Valide un fichier uploadé avant traitement (taille, extension).
//  *
//  * Principe SRP : unique responsabilité → valider les contraintes fichier.
//  * Clean code   : encapsule les règles métier de validation sans logique métier.
//  */
// @Component
// public class FileValidator {

//     private static final long    DEFAULT_MAX_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB
//     private static final Set<String> ALLOWED_EXTENSIONS =
//         Set.of("pdf", "docx", "xlsx", "jpg", "jpeg", "png", "txt");

//     /**
//      * Valide un fichier uploadé.
//      *
//      * @param file fichier multipart (peut être null)
//      * @throws IllegalArgumentException si vide, trop grand, ou extension non autorisée
//      */
//     public void validate(MultipartFile file) {
//         if (file == null || file.isEmpty()) {
//             throw new IllegalArgumentException("Fichier null ou vide");
//         }
//         if (file.getSize() > DEFAULT_MAX_SIZE_BYTES) {
//             throw new IllegalArgumentException(
//                 "Fichier trop volumineux : " + file.getSize() + " bytes");
//         }
//         String filename = file.getOriginalFilename();
//         if (filename != null && filename.contains(".")) {
//             String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
//             if (!ALLOWED_EXTENSIONS.contains(ext)) {
//                 throw new IllegalArgumentException("Extension non autorisée : " + ext);
//             }
//         }
//     }
// }
