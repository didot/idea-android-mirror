/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lang.roomSql

class RoomUnresolvedReferenceInspectionTest : LightRoomTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(RoomUnresolvedReferenceInspection::class.java)
  }

  fun testInvalidColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE <error descr="Cannot resolve symbol 'age'">age</error> > 18")
          List<User> getAdults();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testValidColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM user WHERE name IS NOT NULL")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testInvalidTable() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT * FROM <error>madeup</error>")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testIncompleteQuery_validColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT <error descr="Cannot resolve symbol 'name'">name</error>")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testIncompleteQuery_invalidColumn() {
    myFixture.addRoomEntity("com.example.User","name" ofType "String")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT <error descr="Cannot resolve symbol 'madeup'">madeup</error> name")
          List<User> getUsersWithName();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_valid() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT n * 2 FROM (SELECT age AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_invalidOuterQuery() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT <error descr="Cannot resolve symbol 'madeup'">madeup</error> * 2 FROM (SELECT age AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_validOuterQuery() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT n * 2 FROM (SELECT age AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testSubquery_invalidInnerQuery() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("SELECT n * 2 FROM (SELECT <error>madeup</error> AS n from user)")
          List<Integer> getNumbers();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testDelete() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM user WHERE age > 18")
          void deleteAdults();
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("DELETE FROM <error>foo</error> WHERE <error>bar</error> > 18")
          void deleteAdults();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testUpdate() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET age = age+1 WHERE id=:id")
          void birthday(int id);
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("UPDATE user SET age = user.age+1 WHERE id=:id")
          void birthday(int id);
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("UPDATE <error>foo</error> SET <error>bar</error> = <error>baz</error>+1 WHERE <error>quux</error> = :id")
          void birthday(int id);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testInsert() {
    myFixture.addRoomEntity("com.example.User","age" ofType "int", "id" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH vals AS (SELECT :id, :age) INSERT INTO user SELECT * FROM vals")
          void insert(int id, int age);
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("INSERT INTO user WITH vals AS (SELECT :id, :age) SELECT * FROM vals")
          void insert(int id, int age);
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("INSERT INTO <error>foo</error> WITH vals AS (SELECT :id, :age) SELECT * FROM <error>bar</error>")
          void insert(int id, int age);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testWhereSubquery_withClause() {
    myFixture.addRoomEntity("com.example.Aaa", "a" ofType "int")
    myFixture.addRoomEntity("com.example.Bbb", "b" ofType "int")
    myFixture.addRoomEntity("com.example.Ccc", "c" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (VALUES(1)) SELECT a FROM Aaa WHERE a IN (WITH t2 AS (VALUES(2)) SELECT b FROM Bbb WHERE a IN t2)") List<Integer> getAll();
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH t1 AS (VALUES(1)) SELECT a FROM Aaa WHERE a IN (WITH t2 AS (VALUES(2)) SELECT b FROM Bbb WHERE <error>foo</error> IN <error>bar</error>)") List<Integer> getAll();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testWhereSubquery_aliases() {
    myFixture.addRoomEntity("com.example.Aaa", "a" ofType "int")

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH minmax AS (SELECT (SELECT min(a) as min_a FROM Aaa), (SELECT max(a) FROM Aaa) as max_a) SELECT * FROM Aaa WHERE a=(SELECT max_a FROM minmax)")
          List<Aaa> get();
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("UserDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface UserDao {
          @Query("WITH minmax AS (SELECT (SELECT min(a) as min_a FROM Aaa), (SELECT max(a) FROM Aaa) as max_a) SELECT * FROM Aaa WHERE a=(SELECT <error>foo</error> FROM minmax)")
          List<Aaa> get();
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testParameters() {
    myFixture.configureByText("SomeDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface SomeDao {
          @Query("SELECT :<error>typo</error>")
          String echo(String text);
        }
    """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText("SomeDao.java", """
        package com.example;

        import android.arch.persistence.room.Dao;
        import android.arch.persistence.room.Query;
        import java.util.List;

        @Dao
        public interface SomeDao {
          @Query("SELECT :text")
          String echo(String text);
        }
    """.trimIndent())

    myFixture.checkHighlighting()
  }
}
