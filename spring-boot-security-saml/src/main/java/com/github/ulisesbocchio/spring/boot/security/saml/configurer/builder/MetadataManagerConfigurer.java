package com.github.ulisesbocchio.spring.boot.security.saml.configurer.builder;

import com.github.ulisesbocchio.spring.boot.security.saml.configurer.ServiceProviderSecurityBuilder;
import com.github.ulisesbocchio.spring.boot.security.saml.configurer.ServiceProviderSecurityConfigurer;
import com.github.ulisesbocchio.spring.boot.security.saml.properties.SAMLSsoProperties;
import com.github.ulisesbocchio.spring.boot.security.saml.resource.SpringResourceWrapperOpenSAMLResource;
import lombok.SneakyThrows;
import org.opensaml.saml2.metadata.provider.AbstractMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataFilter;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.ResourceBackedMetadataProvider;
import org.opensaml.xml.parse.ParserPool;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.metadata.MetadataManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configures Metadata Manager
 */
 public class MetadataManagerConfigurer extends SecurityConfigurerAdapter<ServiceProviderSecurityConfigurer, ServiceProviderSecurityBuilder> {

    List<MetadataProvider> metadataProviders = new ArrayList<>();
    private MetadataFilter metadataFilter = null;
    private ExtendedMetadata extendedMetadata = null;
    private Boolean forceMetadataRevocationCheck = null;
    private Boolean metadataRequireSignature = null;
    private Boolean metadataTrustCheck = null;
    private Set<String> metadataTrustedKeys = null;
    private Boolean requireValidMetadata = null;
    private List<String> metadataProviderLocations = new ArrayList<>();
    private MetadataManager metadataManager;
    private ResourceLoader resourceLoader;

    @Override
    public void init(ServiceProviderSecurityBuilder builder) throws Exception {
        resourceLoader = builder.getSharedObject(ResourceLoader.class);
        metadataManager = builder.getSharedObject(MetadataManager.class);
    }

    @Override
    public void configure(ServiceProviderSecurityBuilder builder) throws Exception {
        if(metadataProviders.size() == 0 && metadataProviderLocations.size() > 0) {
            for(String metadataLocation : metadataProviderLocations) {
                MetadataProvider defaultProvider = new ResourceBackedMetadataProvider(new Timer(),
                        new SpringResourceWrapperOpenSAMLResource(resourceLoader.getResource(metadataLocation)));
                metadataProviders.add(defaultProvider);
            }
        }

        if(metadataProviders.size() == 0) {
            String metadataLocation = builder.getSharedObject(SAMLSsoProperties.class).getIdps().getMetadataLocation();
            for(String location : metadataLocation.split(",")) {
                MetadataProvider defaultProvider = new ResourceBackedMetadataProvider(new Timer(),
                        new SpringResourceWrapperOpenSAMLResource(resourceLoader.getResource(location.trim())));
                metadataProviders.add(defaultProvider);
            }
        }

        List<MetadataProvider> extendedMetadataDelegates = metadataProviders.stream()
            .map(this::setParserPool)
            .map(this::getExtendedProvider)
            .collect(Collectors.toList());
        metadataManager.setProviders(extendedMetadataDelegates);
    }

    private MetadataProvider setParserPool(MetadataProvider provider) {
        if(provider instanceof AbstractMetadataProvider) {
            ((AbstractMetadataProvider) provider).setParserPool(getBuilder().getSharedObject(ParserPool.class));
        }
        return provider;
    }

    @SneakyThrows
    private ExtendedMetadataDelegate getExtendedProvider(MetadataProvider provider) {
        if(provider instanceof ExtendedMetadataDelegate) {
            return (ExtendedMetadataDelegate) provider;
        }
        if(extendedMetadata == null) {
            extendedMetadata = getBuilder().getSharedObject(ExtendedMetadata.class);
        }
        ExtendedMetadataDelegate extendedMetadataDelegate = new ExtendedMetadataDelegate(provider, extendedMetadata);
        SAMLSsoProperties.ExtendedMetadataDelegateConfiguration extendedDelegate = getBuilder().getSharedObject(SAMLSsoProperties.class).getExtendedDelegate();

        extendedMetadataDelegate.setForceMetadataRevocationCheck(Optional.ofNullable(forceMetadataRevocationCheck)
                .orElseGet(extendedDelegate::isForceMetadataRevocationCheck));

        extendedMetadataDelegate.setMetadataRequireSignature(Optional.ofNullable(metadataRequireSignature)
                .orElseGet(extendedDelegate::isMetadataRequireSignature));

        extendedMetadataDelegate.setMetadataTrustCheck(Optional.ofNullable(metadataTrustCheck)
                .orElseGet(extendedDelegate::isMetadataTrustCheck));

        extendedMetadataDelegate.setMetadataTrustedKeys(Optional.ofNullable(metadataTrustedKeys)
                .orElseGet(extendedDelegate::getMetadataTrustedKeys));

        extendedMetadataDelegate.setRequireValidMetadata(Optional.ofNullable(requireValidMetadata)
                .orElseGet(extendedDelegate::isRequireValidMetadata));

        extendedMetadataDelegate.setMetadataFilter(Optional.ofNullable(metadataFilter)
                .map(this::postProcess)
                .orElse(null));

        return postProcess(extendedMetadataDelegate);
    }

    public MetadataManagerConfigurer metadataProvider(MetadataProvider provider) {
        metadataProviders.add(provider);
        return this;
    }

    public MetadataManagerConfigurer metadataProviders(MetadataProvider... providers) {
        metadataProviders = Arrays.asList(providers);
        return this;
    }

    public MetadataManagerConfigurer metadataLocations(String... providerLocation) {
        metadataProviderLocations.addAll(Arrays.asList(providerLocation));
        return this;
    }

    public MetadataManagerConfigurer metadataProviders(List<MetadataProvider> providers) {
        metadataProviders = new ArrayList<>(providers);
        return this;
    }

    public MetadataManagerConfigurer metadataFilter(MetadataFilter filter) {
        metadataFilter = filter;
        return this;
    }

    public MetadataManagerConfigurer extendedMetadata(ExtendedMetadata extendedMetadata) {
        getBuilder().setSharedObject(ExtendedMetadata.class, extendedMetadata);
        this.extendedMetadata = extendedMetadata;
        return this;
    }

    public MetadataManagerConfigurer forceMetadataRevocationCheck(boolean forceMetadataRevocationCheck) {
        this.forceMetadataRevocationCheck = forceMetadataRevocationCheck;
        return this;
    }

    public MetadataManagerConfigurer metadataRequireSignature(boolean metadataRequireSignature) {
        this.metadataRequireSignature = metadataRequireSignature;
        return this;
    }

    public MetadataManagerConfigurer metadataTrustCheck(boolean metadataTrustCheck) {
        this.metadataTrustCheck = metadataTrustCheck;
        return this;
    }

    public MetadataManagerConfigurer metadataTrustedKeys(Set<String> metadataTrustedKeys) {
        this.metadataTrustedKeys = metadataTrustedKeys;
        return this;
    }

    public MetadataManagerConfigurer requireValidMetadata(boolean requireValidMetadata) {
        this.requireValidMetadata = requireValidMetadata;
        return this;
    }
}