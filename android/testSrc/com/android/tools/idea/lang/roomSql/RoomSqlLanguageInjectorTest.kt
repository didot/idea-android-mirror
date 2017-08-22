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

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import org.jetbrains.android.AndroidTestCase

class RoomSqlLanguageInjectorTest : AndroidTestCase() {
  private fun checkNoInjection(text: String) {
    assertFalse(InjectedLanguageUtil.hasInjections(myFixture.findElementByText(text, PsiLanguageInjectionHost::class.java)!!))
  }

  private fun checkInjection(text: String, block: (PsiFile, List<Shred>) -> Unit) {
    val host = myFixture.findElementByText(text, PsiLanguageInjectionHost::class.java)!!
    var injectionsCount = 0

    InjectedLanguageUtil.enumerate(host) { injectedPsi, places ->
      assertEquals("More than one injection", 0, injectionsCount++)
      block.invoke(injectedPsi, places)
    }
  }

  fun testSanityCheck() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          @com.example.MadeUp("select * from User")
          List<User> findAll();
        }""".trimIndent()
    )

    checkNoInjection("* from User")
  }

  fun testSimpleQuery() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          @Query("select * from User")
        List<User> findAll();
      }""".trimIndent()
    )

    checkInjection("* from") { psi, _ ->
      assertSame(ROOM_SQL_LANGUAGE, psi.language)
      assertEquals("select * from User", psi.text)
    }
  }

  fun testConcatenation() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          String TABLE = "User";

          @Query("select * from " + TABLE + " where id = :id")
          User findById(int id);
        }
        """.trimIndent()
    )

    checkInjection("* from") { psi, places ->
      assertSame(ROOM_SQL_LANGUAGE, psi.language)
      assertEquals("select * from User where id = :id", psi.text)
      assertEquals(2, places.size)
    }
  }

  fun testStringConstants() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        interface UserDao {
          String ENTITY = "User";
          String TABLE = ENTITY + "Table";

          @Query("select * from " + TABLE)
          List<User> findAll();
        }
        """.trimIndent()
    )

    checkInjection("* from") { psi, _ ->
      assertSame(ROOM_SQL_LANGUAGE, psi.language)
      assertEquals("select * from UserTable where id = :id", psi.text)
    }
  }

  fun testSqliteDatabase() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.room.Query;

        class Util {
          void f(android.database.sqlite.SQLiteDatabase db) {
            db.execSQL("delete from User");
          }
        }
        """.trimIndent()
    )

    checkInjection("delete from") { psi, _ ->
      assertSame(ROOM_SQL_LANGUAGE, psi.language)
      assertEquals("delete from User", psi.text)
    }
  }

  fun testSupportSqliteDatabase() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.arch.persistence.db.SupportSQLiteDatabase;

        class Util {
          void f(SupportSQLiteDatabase db) {
            db.execSQL("delete from User");
          }
        }
        """.trimIndent()
    )

    checkInjection("delete from") { psi, _ ->
      assertSame(ROOM_SQL_LANGUAGE, psi.language)
      assertEquals("delete from User", psi.text)
    }
  }

  fun testSqliteDatabase_nested() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.execSQL(getQuery("foo"));
          }
        }
        """.trimIndent()
    )

    checkNoInjection("foo")
  }

  fun testSqliteDatabase_wrongArgument() {
    myFixture.configureByText(
        JavaFileType.INSTANCE,
        """
        package com.example;

        import android.database.sqlite.SQLiteDatabase;

        class Util {
          void f(SQLiteDatabase db) {
            db.rawQueryWithFactory(null, "select * from User", null, "tableName", null);
          }
        }
        """.trimIndent()
    )

    checkInjection("* from") { psi, _ ->
      assertSame(ROOM_SQL_LANGUAGE, psi.language)
    }

    checkNoInjection("tableName")
  }

}
