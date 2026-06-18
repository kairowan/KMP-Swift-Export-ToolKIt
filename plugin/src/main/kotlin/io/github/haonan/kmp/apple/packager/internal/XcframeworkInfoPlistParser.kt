package io.github.haonan.kmp.apple.packager.internal

import java.io.File
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

/**
 * Parses the subset of XCFramework `Info.plist` metadata needed for artifact validation.
 *
 * Author: kairowan
 */
internal object XcframeworkInfoPlistParser {
    fun parse(file: File): XcframeworkBundleInfo {
        if (!file.exists()) {
            throw GradleException("XCFramework Info.plist does not exist at ${file.absolutePath}.")
        }

        val document = newSecureDocumentBuilderFactory()
            .newDocumentBuilder()
            .apply {
                // XCFramework plists often include the Apple DOCTYPE declaration. Resolve it to an
                // empty in-memory source so parsing stays offline and does not expand external entities.
                setEntityResolver(
                    EntityResolver { _, _ ->
                        InputSource(StringReader(""))
                    }
                )
            }
            .parse(file)
        val plistElement = document.documentElement
        if (plistElement.tagName != "plist") {
            throw GradleException("Expected plist root element in ${file.absolutePath}.")
        }
        val rootDict = plistElement.firstChildElement("dict")
            ?: throw GradleException("Expected top-level dict in ${file.absolutePath}.")
        val rootValues = readDict(rootDict)

        val availableLibraries = rootValues.readArray("AvailableLibraries").map { libraryElement ->
            val libraryValues = readDict(libraryElement)
            XcframeworkLibraryInfo(
                libraryIdentifier = libraryValues.readRequiredString("LibraryIdentifier"),
                libraryPath = libraryValues.readRequiredString("LibraryPath"),
                binaryPath = libraryValues.readRequiredString("BinaryPath"),
                supportedPlatform = libraryValues.readRequiredString("SupportedPlatform"),
                supportedPlatformVariant = libraryValues.readOptionalString("SupportedPlatformVariant"),
                supportedArchitectures = libraryValues.readStringArray("SupportedArchitectures"),
            )
        }

        return XcframeworkBundleInfo(
            formatVersion = rootValues.readOptionalString("XCFrameworkFormatVersion"),
            bundlePackageType = rootValues.readOptionalString("CFBundlePackageType"),
            availableLibraries = availableLibraries,
        )
    }

    private fun readDict(dictElement: Element): Map<String, Element> {
        val children = dictElement.childElements()
        val values = linkedMapOf<String, Element>()
        var index = 0
        while (index < children.size) {
            val keyElement = children[index]
            if (keyElement.tagName != "key") {
                throw GradleException("Expected <key> inside plist dict but found <${keyElement.tagName}>.")
            }
            val valueElement = children.getOrNull(index + 1)
                ?: throw GradleException("Missing plist value for key ${keyElement.textContent.trim()}.")
            values[keyElement.textContent.trim()] = valueElement
            index += 2
        }
        return values
    }

    private fun Map<String, Element>.readRequiredString(key: String): String {
        val value = readOptionalString(key)
            ?: throw GradleException("Missing required plist string value for key $key.")
        if (value.isBlank()) {
            throw GradleException("Required plist string value for key $key must not be blank.")
        }
        return value
    }

    private fun Map<String, Element>.readOptionalString(key: String): String? {
        val element = this[key] ?: return null
        if (element.tagName != "string") {
            throw GradleException("Expected plist string for key $key but found <${element.tagName}>.")
        }
        return element.textContent.trim()
    }

    private fun Map<String, Element>.readArray(key: String): List<Element> {
        val element = this[key]
            ?: throw GradleException("Missing required plist array value for key $key.")
        if (element.tagName != "array") {
            throw GradleException("Expected plist array for key $key but found <${element.tagName}>.")
        }
        return element.childElements().filter { child -> child.tagName == "dict" }
    }

    private fun Map<String, Element>.readStringArray(key: String): List<String> {
        val element = this[key] ?: return emptyList()
        if (element.tagName != "array") {
            throw GradleException("Expected plist array for key $key but found <${element.tagName}>.")
        }
        return element.childElements()
            .mapNotNull { child ->
                if (child.tagName == "string") {
                    child.textContent.trim()
                } else {
                    null
                }
            }
    }

    private fun Element.firstChildElement(tagName: String): Element? {
        return childElements().firstOrNull { child -> child.tagName == tagName }
    }

    private fun Element.childElements(): List<Element> {
        val elements = mutableListOf<Element>()
        val nodeList = childNodes
        for (index in 0 until nodeList.length) {
            val child = nodeList.item(index)
            if (child.nodeType == Node.ELEMENT_NODE) {
                elements += child as Element
            }
        }
        return elements
    }

    private fun newSecureDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
            isCoalescing = true
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
    }
}

internal data class XcframeworkBundleInfo(
    val formatVersion: String?,
    val bundlePackageType: String?,
    val availableLibraries: List<XcframeworkLibraryInfo>,
)

internal data class XcframeworkLibraryInfo(
    val libraryIdentifier: String,
    val libraryPath: String,
    val binaryPath: String,
    val supportedPlatform: String,
    val supportedPlatformVariant: String?,
    val supportedArchitectures: List<String>,
)
