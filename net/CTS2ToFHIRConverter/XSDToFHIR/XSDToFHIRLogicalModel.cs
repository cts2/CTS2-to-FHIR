using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Xml.Schema;
using NPOI.HSSF.UserModel;
using NPOI.SS.UserModel;

namespace XSDToFHIR
{
	/// <summary>
	/// Generates a FHIR "Logical Model" spreadsheet for the complex types in a given XSD
	/// </summary>
	public class XSDToFHIRLogicalModel
	{
		/// <summary>
		/// Provides the source schema for the conversion
		/// </summary>
		public XmlSchemaSet SourceSchemaSet { get; set; }

		/// <summary>
		/// Provides the Fhir directory
		/// </summary>
		public string FhirDirectory { get; set; }

		/// <summary>
		/// Provides the directory that will contain the output logical model(s)
		/// </summary>
		public string TargetDirectory { get; set; }

		private HashSet<XmlSchemaComplexType> convertedTypes = new HashSet<XmlSchemaComplexType>();

		public void Convert()
		{
			// For each schema in the schema set
			foreach (XmlSchema schema in SourceSchemaSet.Schemas())
			{
				// For each complex type in the SourceSchemaSet
				foreach (XmlSchemaObject value in schema.SchemaTypes.Values)
				{
					XmlSchemaComplexType complexType = value as XmlSchemaComplexType;
					if (complexType != null)
					{
						ConvertType(complexType);
					}
				}
			}
		}

		private void CopySourceTemplateFileName(string templateDirectory, string templateType, string sourceName, string targetName)
		{
			string templateFileName = Path.Combine(templateDirectory, String.Format("{0}{1}", sourceName, templateType));
			string targetFileName = Path.Combine(TargetDirectory, String.Format("{0}{1}", targetName, templateType));
			File.Copy(templateFileName, targetFileName, true);
		}

		private void CopySourceTemplate(string typeName)
		{
			// Copy <FHIRDirectory>/source/templates/template-spreadsheet.xml, template-introduction.xml, template-notes.xml to
			// <TargetDirectory>/typename-spreadsheet.xml, typename-introduction.xml, typename-notes.xml
			string templateDirectory = Path.Combine(FhirDirectory, "source", "templates");
			CopySourceTemplateFileName(templateDirectory, "-spreadsheet.xml", "template", typeName);
			CopySourceTemplateFileName(templateDirectory, "-introduction.xml", "template", typeName);
			CopySourceTemplateFileName(templateDirectory, "-notes.xml", "template", typeName);
		}

		private string GetLogicalModelName(XmlSchemaComplexType complexType)
		{
			// TODO: create FHIR-ized type name? Replace uppercase letters other than the first with a dash, then to lower...
			return complexType.Name.ToLower();
		}

		private string GetDocumentName(XmlSchemaComplexType complexType)
		{
			return Path.Combine(TargetDirectory, String.Format("{0}-spreadsheet.xml", GetLogicalModelName(complexType)));
		}

		private void ConvertType(XmlSchemaComplexType complexType)
		{
			if (!convertedTypes.Contains(complexType))
			{
				convertedTypes.Add(complexType);

				// Create a copy of the "source" resource template that will contain the resulting "Logical Model"
				CopySourceTemplate(GetLogicalModelName(complexType));

				// For each element of the complex type, add an element to the logical model
				GenerateElements(complexType);
			}
		}

		private int GetColumnIndex(IRow row, string columnName)
		{
			foreach (ICell cell in row)
				if (cell.CellType == CellType.STRING && cell.StringCellValue == columnName)
					return cell.ColumnIndex;

			return -1;
		}

		private IRow EnsureRow(ISheet sheet, int rowIndex)
		{
			var row = sheet.GetRow(rowIndex);
			if (row == null)
				row = sheet.CreateRow(rowIndex);

			return row;
		}

		private ICell EnsureCell(IRow row, int columnIndex)
		{
			var cell = row.GetCell(columnIndex);
			if (cell == null)
				cell = row.CreateCell(columnIndex);

			return cell;
		}

		private void GenerateElements(XmlSchemaComplexType complexType)
		{
			var workbook = new HSSFWorkbook(File.Open(GetDocumentName(complexType), FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite));

			var sheet = workbook.GetSheet("Data Elements");

			var headerRow = sheet.GetRow(0);
			var firstRow = EnsureRow(sheet, 1);

			int elementIndex = GetColumnIndex(headerRow, "Element");
			int typeIndex = GetColumnIndex(headerRow, "Type");
			int shortLabelIndex = GetColumnIndex(headerRow, "Short Label");
			int definitionIndex = GetColumnIndex(headerRow, "Definition");

			var elementCell = EnsureCell(firstRow, elementIndex);
			elementCell.SetCellValue(complexType.Name);

			var typeCell = EnsureCell(firstRow, typeIndex);
			typeCell.SetCellValue("Logical");

			var shortLabelCell = EnsureCell(firstRow, shortLabelIndex);
			shortLabelCell.SetCellValue(complexType.Name);
		}
	}
}
