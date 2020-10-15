-- ------------------------  用户表 ------------------------ 
CREATE TABLE "USER" (
    "ID" NUMBER(10) NOT NULL,
    "name" VARCHAR2(50 BYTE) NULL ,
    "AGE" NUMBER(10) DEFAULT 0,
    "AVATAR" VARCHAR2(50 BYTE) NULL
);

ALTER TABLE "USER" ADD CHECK ("ID" IS NOT NULL);
ALTER TABLE "USER" ADD PRIMARY KEY ("ID");

-- Oracel使用 Sequence和Trigger来实现自增id
CREATE SEQUENCE USER_ID_SEQ 
  INCREMENT BY 1 -- 每次加1
  START WITH 1 -- 从1开始
  NOMAXVALUE -- 不设最大值
  NOCYCLE -- 一直累加，不循环
  NOCACHE -- 不建缓冲区
  ;
-- 定义Trigger
CREATE OR REPLACE TRIGGER USER_ID_TRIG BEFORE
INSERT ON USER FOR EACH ROW WHEN (new.ID is NULL)
BEGIN
  SELECT USER_ID_SEQ.NEXTVAL INTO:new.ID FROM dual;
END;
/ 

-- ------------------------ 地址表 ------------------------ 
CREATE TABLE "ADDRESS" (
    "ID" NUMBER(10) NOT NULL,
    "USER_ID" NUMBER(10) NOT NULL DEFAULT 0,
    "ADDR" VARCHAR2(50 BYTE) NULL ,
    "TEL" VARCHAR2(50 BYTE) NULL,
    "IS_HOME" NUMBER(10) DEFAULT 0
);

ALTER TABLE "ADDRESS" ADD CHECK ("ID" IS NOT NULL);
ALTER TABLE "ADDRESS" ADD PRIMARY KEY ("ID");

-- Oracel使用 Sequence和Trigger来实现自增id
CREATE SEQUENCE ADDRESS_ID_SEQ 
  INCREMENT BY 1 -- 每次加1
  START WITH 1 -- 从1开始
  NOMAXVALUE -- 不设最大值
  NOCYCLE -- 一直累加，不循环
  NOCACHE -- 不建缓冲区
  ;
-- 定义Trigger
CREATE OR REPLACE TRIGGER ADDRESS_ID_TRIG BEFORE
INSERT ON ADDRESS FOR EACH ROW WHEN (new.ID is NULL)
BEGIN
  SELECT ADDRESS_ID_SEQ.NEXTVAL INTO:new.ID FROM dual;
END;
/

-- ------------------------ 包裹表 ------------------------
CREATE TABLE "PARCEL" (
    "ID" NUMBER(10) NOT NULL,
    "SENDER_ID" NUMBER(10) NOT NULL DEFAULT 0,
    "RECEIVER_ID" NUMBER(10) NOT NULL DEFAULT 0,
    "CONTENT" VARCHAR2(50 BYTE) NULL
);

ALTER TABLE "PARCEL" ADD CHECK ("ID" IS NOT NULL);
ALTER TABLE "PARCEL" ADD PRIMARY KEY ("ID");

-- Oracel使用 Sequence和Trigger来实现自增id
CREATE SEQUENCE PARCEL_ID_SEQ
  INCREMENT BY 1 -- 每次加1
  START WITH 1 -- 从1开始
  NOMAXVALUE -- 不设最大值
  NOCYCLE -- 一直累加，不循环
  NOCACHE -- 不建缓冲区
  ;
-- 定义Trigger
CREATE OR REPLACE TRIGGER PARCELS_ID_TRIG BEFORE
INSERT ON PARCELS FOR EACH ROW WHEN (new.ID is NULL)
BEGIN
  SELECT PARCELS_ID_SEQ.NEXTVAL INTO:new.ID FROM dual;
END;
/

-- ------------------------ 消息表 ------------------------
CREATE TABLE "MESSAGE" (
    "ID" NUMBER(10) NOT NULL,
    "FROM_UID" NUMBER(10) NOT NULL DEFAULT 0,
    "TO_UID" NUMBER(10) NOT NULL DEFAULT 0,
    "CONTENT" VARCHAR2(50 BYTE) NULL
);

ALTER TABLE "MESSAGE" ADD CHECK ("ID" IS NOT NULL);
ALTER TABLE "MESSAGE" ADD PRIMARY KEY ("ID");

-- Oracel使用 Sequence和Trigger来实现自增id
CREATE SEQUENCE MESSAGE_ID_SEQ
  INCREMENT BY 1 -- 每次加1
  START WITH 1 -- 从1开始
  NOMAXVALUE -- 不设最大值
  NOCYCLE -- 一直累加，不循环
  NOCACHE -- 不建缓冲区
  ;
-- 定义Trigger
CREATE OR REPLACE TRIGGER MESSAGES_ID_TRIG BEFORE
INSERT ON MESSAGES FOR EACH ROW WHEN (new.ID is NULL)
BEGIN
  SELECT MESSAGES_ID_SEQ.NEXTVAL INTO:new.ID FROM dual;
END;
/