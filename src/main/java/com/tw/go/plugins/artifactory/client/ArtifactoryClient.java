package com.tw.go.plugins.artifactory.client;

import com.google.common.base.Function;
import com.tw.go.plugins.artifactory.Logger;
import com.tw.go.plugins.artifactory.model.ArtifactUploadMetadata;
import com.tw.go.plugins.artifactory.model.GoArtifact;
import com.tw.go.plugins.artifactory.model.GoBuildDetails;
import org.jfrog.build.api.Build;
import org.jfrog.build.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.client.ArtifactoryUploadResponse;
import org.jfrog.build.client.DeployDetails;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.toMap;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.chomp;

public class ArtifactoryClient implements Closeable {
    private Logger logger = Logger.getLogger(getClass());

    // TODO: Needs a better name
    private BuildMap buildMap = new BuildMap();

    private ArtifactoryBuildInfoClient buildInfoClient;

    public ArtifactoryClient(String artifactoryUrl, String user, String password) {
        this.buildInfoClient = new ArtifactoryBuildInfoClient(artifactoryUrl, user, password, logger);
    }

    // TODO: is this really required?
    ArtifactoryClient(ArtifactoryBuildInfoClient buildInfoClient) {
        this.buildInfoClient = buildInfoClient;
    }

    public Collection<ArtifactUploadMetadata> uploadArtifacts(Collection<GoArtifact> artifacts) {
        return newArrayList(transform(artifacts, new Function<GoArtifact, ArtifactUploadMetadata>() {
            @Override
            public ArtifactUploadMetadata apply(GoArtifact artifact) {
                return new ArtifactUploadMetadata(upload(artifact));
            }
        }));
    }

    public void uploadBuildDetails(GoBuildDetails details) {
        Build build = buildMap.apply(details);
        try {
            buildInfoClient.sendBuildInfo(build);
        } catch (IOException e) {
            throw new RuntimeException(format("Unable to upload build info : %s", e.getMessage()), e);
        }
    }

    @Override
    public void close() {
        buildInfoClient.shutdown();
    }

    private ArtifactoryUploadResponse upload(GoArtifact artifact) {
        File artifactFile = new File(artifact.localPath());

        try {
            DeployDetails deployDetails = new DeployDetails.Builder()
                    .targetRepository(artifact.repository())
                    .artifactPath(artifact.remotePath())
                    .file(artifactFile)
                    .sha1(artifact.sha1())
                    .md5(artifact.md5())
                    .addProperties(removeTrailingSlashes(artifact.properties()))
                    .build();

            return buildInfoClient.deployArtifact(deployDetails);
        } catch (IOException e) {
            throw new RuntimeException(format("Unable to upload artifact %s : %s", artifact, e.getMessage()), e);
        }
    }

    private Map<String, String> removeTrailingSlashes(final Map<String, String> properties) {
        return toMap(properties.keySet(), new Function<String, String>() {
            @Override
            public String apply(String key) {
                return chomp(properties.get(key), "/");
            }
        });
    }
}
