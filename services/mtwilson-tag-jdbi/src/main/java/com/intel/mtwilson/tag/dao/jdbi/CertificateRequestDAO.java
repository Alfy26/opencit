/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.tag.dao.jdbi;

import com.intel.mtwilson.tag.model.CertificateRequest;
import com.intel.dcsg.cpg.io.UUID;
import java.io.Closeable;
import java.util.List;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterArgumentFactory;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

@RegisterArgumentFactory(UUIDArgument.class)
@RegisterMapper(CertificateRequestResultMapper.class)
public interface CertificateRequestDAO extends Closeable{
    // Note:  if you change the table definition (for example uuid from binary to char) also check the TagResultMapper class that is used for jdbi queries
//    @SqlUpdate("create table tag (id bigint primary key generated always as identity, uuid char(16) for bit data, name varchar(100), oid varchar(255))")   // jooq tries to cast char(16) for bit data  into a blob for comparisons... don't know why. and it's not possible to search on blob contents (usually not implemented by rdbms because blobs by definition can be gigabytes long), so using char(36) instead to get the standard uuid format
    @SqlUpdate("create table mw_tag_certificate_request (id bigint primary key generated always as identity, uuid char(36), subject varchar(255), selectionId bigint, status varchar(255), certificateId bigint)")
    void create();

    @SqlUpdate("insert into mw_tag_certificate_request (id, subject, selectionId, certificateId, authorityName, status) "
            + "values (:id, :subject, :selectionId, :certificateId, :authorityName, 'New')")
//    @GetGeneratedKeys
    void insert(@Bind("id") UUID id, @Bind("subject") String subject, @Bind("selectionId") UUID selectionId, 
        @Bind("certificateId") UUID certificateId, @Bind("authorityName") String authorityName);

    @SqlUpdate("update mw_tag_certificate_request set status=:status where id=:id")
    void updateStatus(@Bind("id") UUID id, @Bind("status") String status);

    @SqlUpdate("update mw_tag_certificate_request set certificateId=:certificateId, status='Done' where id=:id")
    void updateApproved(@Bind("id") UUID id, @Bind("certificateId") UUID certificateId);

    @SqlUpdate("update mw_tag_certificate_request set authorityName=:authorityName where id=:id")
    void updateAuthority(@Bind("id") UUID id, @Bind("authorityName") String authorityName);
    
    @SqlUpdate("delete from mw_tag_certificate_request where id=:id")
    void delete(@Bind("id") UUID id);

    @SqlQuery("select id, subject, status, selectionId, certificateId from mw_tag_certificate_request where id=:id")
    CertificateRequest findById(@Bind("id") UUID id);
    
    @SqlQuery("select id, subject, status, selectionId, certificateId from mw_tag_certificate_request where subject=:subject")
    List<CertificateRequest> findBySubject(@Bind("subject") String subject);
    
    @Override
    void close();
}
