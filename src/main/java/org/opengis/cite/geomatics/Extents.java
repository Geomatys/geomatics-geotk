package org.opengis.cite.geomatics;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.geotoolkit.geometry.Envelopes;
import org.geotoolkit.geometry.GeneralDirectPosition;
import org.geotoolkit.geometry.GeneralEnvelope;
import org.geotoolkit.geometry.jts.JTS;
import org.geotoolkit.geometry.jts.JTSEnvelope2D;
import org.geotoolkit.gml.GeometrytoJTS;
import org.geotoolkit.gml.xml.AbstractGeometry;
import org.geotoolkit.referencing.CRS;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.geotoolkit.xml.MarshallerPool;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Provides utility methods to create or operate on envelope representations.
 * 
 */
public class Extents {

	private static final String GML_NS = "http://www.opengis.net/gml/3.2";
	private static final GeometryFactory JTS_GEOM_FACTORY = new GeometryFactory();

	private Extents() {
	}

	/**
	 * Calculates the envelope that covers the given collection of GML geometry
	 * elements.
	 * 
	 * @param geomNodes
	 *            A NodeList containing GML geometry elements; it is assumed
	 *            these all refer to the same CRS.
	 * @return An Envelope object representing the overall spatial extent (MBR)
	 *         of the geometries.
	 * @throws JAXBException
	 *             If a node cannot be unmarshalled to a geometry object.
	 */
	@SuppressWarnings("unchecked")
	public static Envelope calculateEnvelope(NodeList geomNodes)
			throws JAXBException {
		Unmarshaller unmarshaller = null;
		try {
			MarshallerPool pool = new MarshallerPool(
					"org.geotoolkit.gml.xml.v321");
			unmarshaller = pool.acquireUnmarshaller();
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
		com.vividsolutions.jts.geom.Envelope envelope = new com.vividsolutions.jts.geom.Envelope();
		CoordinateReferenceSystem crs = null;
		for (int i = 0; i < geomNodes.getLength(); i++) {
			Node node = geomNodes.item(i);
			JAXBElement<AbstractGeometry> result = (JAXBElement<AbstractGeometry>) unmarshaller
					.unmarshal(node);
			AbstractGeometry gmlGeom = result.getValue();
			crs = gmlGeom.getCoordinateReferenceSystem();
			Geometry jtsGeom;
			try {
				jtsGeom = GeometrytoJTS.toJTS(gmlGeom);
			} catch (FactoryException e) {
				throw new RuntimeException(e);
			}
			envelope.expandToInclude(jtsGeom.getEnvelopeInternal());
		}
		return new JTSEnvelope2D(envelope, crs);
	}

	/**
	 * Generates a standard GML representation (gml:Envelope) of an Envelope
	 * object. Ordinates are rounded down to 2 decimal places.
	 * 
	 * @param envelope
	 *            An Envelope defining a bounding rectangle (or prism).
	 * @return A DOM Document with gml:Envelope as the document element.
	 */
	public static Document envelopeAsGML(Envelope envelope) {
		Document doc;
		try {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.newDocument();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		Element gmlEnv = doc.createElementNS(GML_NS, "gml:Envelope");
		doc.appendChild(gmlEnv);
		gmlEnv.setAttribute("srsName", GeodesyUtils.getCRSIdentifier(envelope
				.getCoordinateReferenceSystem()));
		DecimalFormat df = new DecimalFormat("#.##");
		df.setRoundingMode(RoundingMode.DOWN);
		StringBuffer lowerCoord = new StringBuffer();
		StringBuffer upperCoord = new StringBuffer();
		for (int i = 0; i < envelope.getDimension(); i++) {
			lowerCoord.append(df.format(envelope.getMinimum(i)));
			upperCoord.append(df.format(envelope.getMaximum(i)));
			if (i < (envelope.getDimension() - 1)) {
				lowerCoord.append(' ');
				upperCoord.append(' ');
			}
		}
		Element lowerCorner = doc.createElementNS(GML_NS, "gml:lowerCorner");
		lowerCorner.setTextContent(lowerCoord.toString());
		gmlEnv.appendChild(lowerCorner);
		Element upperCorner = doc.createElementNS(GML_NS, "gml:upperCorner");
		upperCorner.setTextContent(upperCoord.toString());
		gmlEnv.appendChild(upperCorner);
		return doc;
	}

	/**
	 * Creates a JTS Polygon having the same extent as the given envelope.
	 * 
	 * @param envelope
	 *            An Envelope defining a bounding rectangle.
	 * @return A Polygon with the relevant CoordinateReferenceSystem set as a
	 *         user data object.
	 */
	public static Polygon envelopeAsPolygon(Envelope envelope) {
		DirectPosition lowerCorner = envelope.getLowerCorner();
		DirectPosition upperCorner = envelope.getUpperCorner();
		LinearRing ring = JTS_GEOM_FACTORY.createLinearRing(new Coordinate[] {
				new Coordinate(lowerCorner.getOrdinate(0), lowerCorner
						.getOrdinate(1)),
				new Coordinate(upperCorner.getOrdinate(0), lowerCorner
						.getOrdinate(1)),
				new Coordinate(upperCorner.getOrdinate(0), upperCorner
						.getOrdinate(1)),
				new Coordinate(lowerCorner.getOrdinate(0), upperCorner
						.getOrdinate(1)),
				new Coordinate(lowerCorner.getOrdinate(0), lowerCorner
						.getOrdinate(1)) });
		Polygon polygon = JTS_GEOM_FACTORY.createPolygon(ring);
		JTS.setCRS(polygon, envelope.getCoordinateReferenceSystem());
		return polygon;
	}

	/**
	 * Coalesces a sequence of bounding boxes so as to create an envelope that
	 * covers them all. The resulting envelope will use the same CRS as the
	 * first bounding box; the remaining bounding boxes will be transformed to
	 * this CRS if necessary.
	 * 
	 * @param bboxNodes
	 *            A list of elements representing common bounding boxes
	 *            (ows:BoundingBox or ows:WGS84BoundingBox).
	 * @return An Envelope encompassing the total extent of the given bounding
	 *         boxes.
	 * @throws FactoryException
	 *             If an unrecognized CRS reference is encountered or a
	 *             corresponding CoordinateReferenceSystem cannot be
	 *             constructed.
	 * @throws TransformException
	 *             If an attempt to perform a coordinate transformation fails
	 *             for some reason.
	 */
	public static Envelope coalesceBoundingBoxes(List<Node> bboxNodes)
			throws FactoryException, TransformException {
		CoordinateReferenceSystem crs = null;
		GeneralEnvelope totalExtent = null;
		for (Node boxNode : bboxNodes) {
			Element bbox = (Element) boxNode;
			String crsRef = bbox.getAttribute("crs");
			if (crsRef.isEmpty() || crsRef.equals(GeodesyUtils.OGC_CRS84)) {
				// lon,lat axis order
				crs = DefaultGeographicCRS.WGS84;
			} else {
				String id = GeodesyUtils.getAbbreviatedCRSIdentifier(crsRef);
				crs = CRS.decode(id);
			}
			String[] lowerCoords = bbox
					.getElementsByTagNameNS(bbox.getNamespaceURI(),
							"LowerCorner").item(0).getTextContent().trim()
					.split("\\s");
			String[] upperCoords = bbox
					.getElementsByTagNameNS(bbox.getNamespaceURI(),
							"UpperCorner").item(0).getTextContent().trim()
					.split("\\s");
			int dim = lowerCoords.length;
			double[] coords = new double[dim * 2];
			for (int i = 0; i < dim; i++) {
				coords[i] = Double.parseDouble(lowerCoords[i]);
				coords[i + dim] = Double.parseDouble(upperCoords[i]);
			}
			if (null == totalExtent) { // first box
				totalExtent = new GeneralEnvelope(crs);
				totalExtent.setEnvelope(coords);
			} else {
				GeneralDirectPosition lowerCorner = new GeneralDirectPosition(
						crs);
				lowerCorner.setLocation(Arrays.copyOfRange(coords, 0, dim));
				GeneralDirectPosition upperCorner = new GeneralDirectPosition(
						crs);
				upperCorner.setLocation(Arrays.copyOfRange(coords, dim,
						coords.length));
				Envelope nextEnv = new GeneralEnvelope(lowerCorner, upperCorner);
				if (!crs.equals(totalExtent.getCoordinateReferenceSystem())) {
					nextEnv = Envelopes.transform(nextEnv,
							totalExtent.getCoordinateReferenceSystem());
				}
				totalExtent.add(nextEnv);
			}
		}
		return totalExtent;
	}

	/**
	 * Returns a String representation of a bounding box suitable for use as a
	 * query parameter value. The value consists of a comma-separated sequence
	 * of items as indicated below:
	 * 
	 * <pre>
	 * LowerCorner coordinate 1
	 * LowerCorner coordinate 2
	 * LowerCorner coordinate N
	 * ...
	 * UpperCorner coordinate 1
	 * UpperCorner coordinate 2
	 * UpperCorner coordinate N
	 * crs URI (optional - default "urn:ogc:def:crs:OGC:1.3:CRS84")
	 * </pre>
	 * 
	 * @param envelope
	 *            An envelope specifying a geographic extent.
	 * @return A String suitable for use as a query parameter value (KVP
	 *         syntax).
	 * 
	 * @see <a target="_blank"
	 *      href="http://portal.opengeospatial.org/files/?artifact_id=38867">OGC
	 *      06-121r9, 10.2.3</a>
	 */
	public static String getEnvelopeAsKVP(Envelope envelope) {
		StringBuilder kvp = new StringBuilder();
		double[] lowerCorner = envelope.getLowerCorner().getCoordinate();
		for (int i = 0; i < lowerCorner.length; i++) {
			kvp.append(lowerCorner[i]).append(',');
		}
		double[] upperCorner = envelope.getUpperCorner().getCoordinate();
		for (int i = 0; i < upperCorner.length; i++) {
			kvp.append(upperCorner[i]).append(',');
		}
		kvp.append(GeodesyUtils.getCRSIdentifier(envelope
				.getCoordinateReferenceSystem()));
		return kvp.toString();
	}
}
